package com.noorazan.app.ui.home

import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.noorazan.app.data.PrayerPreferences
import com.noorazan.app.databinding.FragmentHomeBinding
import com.noorazan.app.model.PrayerName
import com.noorazan.app.scheduler.AzanScheduler
import com.noorazan.app.service.AzanPlaybackService
import com.noorazan.app.util.LocationHelper
import com.noorazan.app.util.PrayTimeCalculator
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: PrayerPreferences
    private var countDownTimer: CountDownTimer? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = PrayerPreferences(requireContext())

        binding.btnRefreshLocation.setOnClickListener {
            binding.tvLocation.text = "Updating GPS location..."
            viewLifecycleOwner.lifecycleScope.launch {
                val success = LocationHelper.fetchAndSaveCurrentLocation(requireContext())
                if (success) {
                    AzanScheduler.scheduleNextPrayerAlarm(requireContext())
                    Toast.makeText(requireContext(), "Location & prayer times updated", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Enable GPS permission to detect location", Toast.LENGTH_SHORT).show()
                }
                bindData()
            }
        }

        binding.btnTestAzan.setOnClickListener {
            AzanPlaybackService.startAzan(requireContext(), PrayerName.DHUHR)
            Toast.makeText(requireContext(), "Playing Azan preview...", Toast.LENGTH_SHORT).show()
        }

        binding.btnStopAzan.setOnClickListener {
            AzanPlaybackService.stopAzan(requireContext())
            Toast.makeText(requireContext(), "Azan stopped", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        bindData()
    }

    override fun onPause() {
        super.onPause()
        countDownTimer?.cancel()
    }

    private fun bindData() {
        if (_binding == null) return
        val now = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
        binding.tvCurrentDate.text = dateFormat.format(Date(now))
        binding.tvLocation.text = prefs.locationName
        binding.tvMethodBadge.text = prefs.calculationMethod.title

        val nextPrayer = PrayTimeCalculator.getNextPrayerForDisplay(prefs, now)
        if (nextPrayer != null) {
            binding.tvNextPrayerName.text = "${nextPrayer.prayer.displayName} (${nextPrayer.prayer.arabicName})"
            binding.tvNextPrayerTime.text = nextPrayer.formattedTime
            binding.tvAzanStatus.text = when {
                !nextPrayer.prayer.isAzanPrayer -> "Sunrise (No Azan)"
                nextPrayer.isEnabled -> "Azan Enabled • Scheduled"
                else -> "Azan Muted in Settings"
            }
            startCountdown(nextPrayer.timestampMillis)
        }

        val schedule = PrayTimeCalculator.getScheduleForDate(Calendar.getInstance(), prefs, now)
        val map = schedule.items.associateBy { it.prayer }
        binding.tvFajrQuick.text = map[PrayerName.FAJR]?.formattedTime ?: "--:--"
        binding.tvDhuhrQuick.text = map[PrayerName.DHUHR]?.formattedTime ?: "--:--"
        binding.tvAsrQuick.text = map[PrayerName.ASR]?.formattedTime ?: "--:--"
        binding.tvMaghribQuick.text = map[PrayerName.MAGHRIB]?.formattedTime ?: "--:--"
        binding.tvIshaQuick.text = map[PrayerName.ISHA]?.formattedTime ?: "--:--"
    }

    private fun startCountdown(targetMillis: Long) {
        countDownTimer?.cancel()
        val diff = targetMillis - System.currentTimeMillis()
        if (diff <= 0) {
            binding.tvCountdown.text = "00:00:00"
            return
        }

        countDownTimer = object : CountDownTimer(diff, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val totalSeconds = millisUntilFinished / 1000L
                val hours = totalSeconds / 3600
                val minutes = (totalSeconds % 3600) / 60
                val seconds = totalSeconds % 60
                _binding?.tvCountdown?.text = String.format(
                    Locale.US,
                    "-%02d:%02d:%02d",
                    hours,
                    minutes,
                    seconds
                )
            }

            override fun onFinish() {
                bindData()
            }
        }.start()
    }

    override fun onDestroyView() {
        countDownTimer?.cancel()
        _binding = null
        super.onDestroyView()
    }
}
