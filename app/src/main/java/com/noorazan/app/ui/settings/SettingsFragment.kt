package com.noorazan.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.noorazan.app.data.PrayerPreferences
import com.noorazan.app.databinding.FragmentSettingsBinding
import com.noorazan.app.model.AsrJuristicMethod
import com.noorazan.app.model.CalculationMethod
import com.noorazan.app.model.PrayerName
import com.noorazan.app.scheduler.AzanScheduler
import com.noorazan.app.service.AzanPlaybackService
import java.util.Locale

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: PrayerPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = PrayerPreferences(requireContext())

        setupCalculationDropdowns()
        setupPrayerSwitches()
        setupOffsetButtons()
        setupAudioAndBatteryActions()
    }

    private fun setupCalculationDropdowns() {
        val methods = CalculationMethod.entries
        val methodAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            methods.map { it.title }
        )
        binding.dropdownCalcMethod.setAdapter(methodAdapter)
        binding.dropdownCalcMethod.setText(prefs.calculationMethod.title, false)
        binding.tvCalcMethodSubtitle.text = prefs.calculationMethod.subtitle

        binding.dropdownCalcMethod.setOnItemClickListener { _, _, position, _ ->
            val selected = methods[position]
            prefs.calculationMethod = selected
            binding.tvCalcMethodSubtitle.text = selected.subtitle
            AzanScheduler.scheduleNextPrayerAlarm(requireContext())
            Toast.makeText(requireContext(), "Calculation method updated", Toast.LENGTH_SHORT).show()
        }

        val asrMethods = AsrJuristicMethod.entries
        val asrAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            asrMethods.map { it.title }
        )
        binding.dropdownAsrMethod.setAdapter(asrAdapter)
        binding.dropdownAsrMethod.setText(prefs.asrMethod.title, false)

        binding.dropdownAsrMethod.setOnItemClickListener { _, _, position, _ ->
            val selected = asrMethods[position]
            prefs.asrMethod = selected
            AzanScheduler.scheduleNextPrayerAlarm(requireContext())
            Toast.makeText(requireContext(), "Asr juristic method updated", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupPrayerSwitches() {
        binding.switchFajr.isChecked = prefs.isAzanEnabled(PrayerName.FAJR)
        binding.switchDhuhr.isChecked = prefs.isAzanEnabled(PrayerName.DHUHR)
        binding.switchAsr.isChecked = prefs.isAzanEnabled(PrayerName.ASR)
        binding.switchMaghrib.isChecked = prefs.isAzanEnabled(PrayerName.MAGHRIB)
        binding.switchIsha.isChecked = prefs.isAzanEnabled(PrayerName.ISHA)

        binding.switchFajr.setOnCheckedChangeListener { _, isChecked ->
            updatePrayerToggle(PrayerName.FAJR, isChecked)
        }
        binding.switchDhuhr.setOnCheckedChangeListener { _, isChecked ->
            updatePrayerToggle(PrayerName.DHUHR, isChecked)
        }
        binding.switchAsr.setOnCheckedChangeListener { _, isChecked ->
            updatePrayerToggle(PrayerName.ASR, isChecked)
        }
        binding.switchMaghrib.setOnCheckedChangeListener { _, isChecked ->
            updatePrayerToggle(PrayerName.MAGHRIB, isChecked)
        }
        binding.switchIsha.setOnCheckedChangeListener { _, isChecked ->
            updatePrayerToggle(PrayerName.ISHA, isChecked)
        }
    }

    private fun updatePrayerToggle(prayer: PrayerName, enabled: Boolean) {
        prefs.setAzanEnabled(prayer, enabled)
        AzanScheduler.scheduleNextPrayerAlarm(requireContext())
    }

    private fun setupOffsetButtons() {
        refreshOffsetLabels()

        binding.btnFajrMinus.setOnClickListener { adjustOffset(PrayerName.FAJR, -1) }
        binding.btnFajrPlus.setOnClickListener { adjustOffset(PrayerName.FAJR, 1) }

        binding.btnDhuhrMinus.setOnClickListener { adjustOffset(PrayerName.DHUHR, -1) }
        binding.btnDhuhrPlus.setOnClickListener { adjustOffset(PrayerName.DHUHR, 1) }

        binding.btnAsrMinus.setOnClickListener { adjustOffset(PrayerName.ASR, -1) }
        binding.btnAsrPlus.setOnClickListener { adjustOffset(PrayerName.ASR, 1) }

        binding.btnMaghribMinus.setOnClickListener { adjustOffset(PrayerName.MAGHRIB, -1) }
        binding.btnMaghribPlus.setOnClickListener { adjustOffset(PrayerName.MAGHRIB, 1) }

        binding.btnIshaMinus.setOnClickListener { adjustOffset(PrayerName.ISHA, -1) }
        binding.btnIshaPlus.setOnClickListener { adjustOffset(PrayerName.ISHA, 1) }

        binding.btnResetOffsets.setOnClickListener {
            PrayerName.azanPrayers().forEach { prefs.setManualOffsetMinutes(it, 0) }
            refreshOffsetLabels()
            AzanScheduler.scheduleNextPrayerAlarm(requireContext())
            Toast.makeText(requireContext(), "All manual adjustments reset to 0 min", Toast.LENGTH_SHORT).show()
        }
    }

    private fun adjustOffset(prayer: PrayerName, delta: Int) {
        val updated = (prefs.getManualOffsetMinutes(prayer) + delta).coerceIn(-60, 60)
        prefs.setManualOffsetMinutes(prayer, updated)
        refreshOffsetLabels()
        AzanScheduler.scheduleNextPrayerAlarm(requireContext())
    }

    private fun refreshOffsetLabels() {
        binding.tvFajrOffset.text = formatOffset(prefs.getManualOffsetMinutes(PrayerName.FAJR))
        binding.tvDhuhrOffset.text = formatOffset(prefs.getManualOffsetMinutes(PrayerName.DHUHR))
        binding.tvAsrOffset.text = formatOffset(prefs.getManualOffsetMinutes(PrayerName.ASR))
        binding.tvMaghribOffset.text = formatOffset(prefs.getManualOffsetMinutes(PrayerName.MAGHRIB))
        binding.tvIshaOffset.text = formatOffset(prefs.getManualOffsetMinutes(PrayerName.ISHA))
    }

    private fun formatOffset(mins: Int): String {
        return when {
            mins > 0 -> String.format(Locale.US, "+%d m", mins)
            mins < 0 -> String.format(Locale.US, "%d m", mins)
            else -> "0 m"
        }
    }

    private fun setupAudioAndBatteryActions() {
        binding.btnTestFajrAzan.setOnClickListener {
            AzanPlaybackService.startAzan(requireContext(), PrayerName.FAJR)
            Toast.makeText(requireContext(), "Testing Fajr Azan (assets/azan/fajr.mp3)", Toast.LENGTH_SHORT).show()
        }

        binding.btnTestNormalAzan.setOnClickListener {
            AzanPlaybackService.startAzan(requireContext(), PrayerName.DHUHR)
            Toast.makeText(requireContext(), "Testing Regular Azan (assets/azan/azan.mp3)", Toast.LENGTH_SHORT).show()
        }

        binding.btnBatteryOptimization.setOnClickListener {
            val pm = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
            val pkg = requireContext().packageName
            if (pm.isIgnoringBatteryOptimizations(pkg)) {
                Toast.makeText(requireContext(), "Battery optimization is already unrestricted.", Toast.LENGTH_SHORT).show()
            } else {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$pkg")
                    }
                    startActivity(intent)
                } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
