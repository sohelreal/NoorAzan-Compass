package com.noorazan.app.ui.qibla

import android.graphics.Color
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.noorazan.app.R
import com.noorazan.app.data.PrayerPreferences
import com.noorazan.app.databinding.FragmentQiblaBinding
import com.noorazan.app.util.CompassEngine
import com.noorazan.app.util.CompassReading
import com.noorazan.app.util.QiblaCalculator
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

/**
 * Qibla finder + digital compass.
 * Uses the fast tilt-compensated CompassEngine and corrects magnetic north to TRUE north
 * using the magnetic declination for the saved location.
 */
class QiblaFragment : Fragment() {

    private var _binding: FragmentQiblaBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: PrayerPreferences
    private var engine: CompassEngine? = null

    private var qiblaTrue = 0f
    private var declination = 0f
    private var useTrueNorth = true
    private var qiblaMode = true

    private var shown = Float.NaN
    private var wasAligned = false
    private var lastInfoUpdate = 0L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQiblaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = PrayerPreferences(requireContext())
        engine = CompassEngine(requireContext()) { reading -> onReading(reading) }

        binding.modeGroup.check(R.id.btnModeQibla)
        binding.modeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                qiblaMode = checkedId == R.id.btnModeQibla
                applyMode()
            }
        }
        binding.btnNorthRef.setOnClickListener {
            useTrueNorth = !useTrueNorth
            binding.btnNorthRef.text = if (useTrueNorth) "North: True" else "North: Magnetic"
        }
        applyMode()
    }

    private fun applyMode() {
        binding.qiblaCompassView.showQibla = qiblaMode
        binding.tvQiblaTitle.text = if (qiblaMode) "Qibla Direction" else "Digital Compass"
        if (!qiblaMode) {
            binding.tvAlignmentBanner.setBackgroundColor(Color.parseColor("#37474F"))
        }
    }

    override fun onResume() {
        super.onResume()
        val lat = prefs.latitude
        val lng = prefs.longitude
        qiblaTrue = QiblaCalculator.calculateQiblaBearing(lat, lng)
        declination = QiblaCalculator.getMagneticDeclination(lat, lng)

        binding.tvQiblaLocation.text = prefs.locationName
        binding.tvQiblaAngle.text = String.format(Locale.US, "Qibla: %.1f° (True)", qiblaTrue)

        val eng = engine
        if (eng == null || !eng.isAvailable) {
            binding.tvSensorStatus.text = "Compass sensor not available on this device."
        } else {
            binding.tvSensorStatus.text = "Starting compass…"
            eng.start()
        }
    }

    override fun onPause() {
        super.onPause()
        engine?.stop()
    }

    private fun norm(a: Float): Float = ((a % 360f) + 360f) % 360f

    private fun onReading(r: CompassReading) {
        val b = _binding ?: return

        val target = norm(r.magneticHeading + if (useTrueNorth) declination else 0f)
        if (shown.isNaN()) shown = target
        val d = ((target - shown + 540f) % 360f) - 180f
        if (abs(d) > 0.25f) {
            shown = norm(shown + d * min(0.7f, 0.18f + abs(d) / 40f))
        }

        // Qibla is a TRUE bearing: shift it when the dial is in magnetic north.
        val markerAngle = if (useTrueNorth) qiblaTrue else norm(qiblaTrue - declination)
        b.qiblaCompassView.updateAngles(shown, markerAngle)
        b.tvHeadingAngle.text = String.format(Locale.US, "Heading: %.0f° %s", shown, cardinal(shown))

        if (qiblaMode) {
            val diff = abs(((markerAngle - shown + 540f) % 360f) - 180f)
            val aligned = diff <= 3f
            if (aligned) {
                b.tvAlignmentBanner.text = "Facing Qibla"
                b.tvAlignmentBanner.setBackgroundColor(Color.parseColor("#1B5E20"))
                if (!wasAligned) vibrateOnce()
            } else {
                b.tvAlignmentBanner.text = String.format(Locale.US, "Turn %.0f° toward Qibla", diff)
                b.tvAlignmentBanner.setBackgroundColor(Color.parseColor("#37474F"))
            }
            wasAligned = aligned
        } else {
            b.tvAlignmentBanner.text = String.format(Locale.US, "%s  %.0f°", cardinal(shown), shown)
            wasAligned = false
        }

        val now = SystemClock.elapsedRealtime()
        if (now - lastInfoUpdate > 500L) {
            lastInfoUpdate = now
            updateSensorStatus(b, r)
        }
    }

    private fun updateSensorStatus(b: FragmentQiblaBinding, r: CompassReading) {
        val field = r.fieldMicroTesla
        val acc = r.accuracyDeg
        val ref = if (useTrueNorth) {
            String.format(Locale.US, "True N (decl %+.1f°)", declination)
        } else {
            "Magnetic N"
        }
        val accText = if (acc >= 0f) String.format(Locale.US, " · ±%.0f°", acc) else ""
        val fieldText = if (field > 0f) String.format(Locale.US, " · %.0f µT", field) else ""

        val text: String
        val color: String
        if (field > 0f && (field < 20f || field > 70f)) {
            text = "⚠ Magnetic interference$fieldText. Move away from metal, magnets or phone cases."
            color = "#B45309"
        } else if (r.calibration <= SensorManager.SENSOR_STATUS_ACCURACY_LOW || acc > 15f) {
            text = "⚠ Calibrate: move your phone in a figure-8 motion."
            color = "#B45309"
        } else {
            text = "✓ $ref$accText$fieldText"
            color = "#1B5E20"
        }
        b.tvSensorStatus.text = text
        b.tvSensorStatus.setTextColor(Color.parseColor(color))
    }

    private fun cardinal(deg: Float): String {
        val names = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        return names[(Math.round(deg / 45f) % 8)]
    }

    @Suppress("DEPRECATION")
    private fun vibrateOnce() {
        try {
            val v = requireContext().getSystemService(Vibrator::class.java) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(60L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                v.vibrate(60L)
            }
        } catch (_: Exception) {
        }
    }

    override fun onDestroyView() {
        engine?.stop()
        engine = null
        _binding = null
        super.onDestroyView()
    }
}
