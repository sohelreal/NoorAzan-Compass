package com.noorazan.app.ui.prayertimes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.noorazan.app.data.PrayerPreferences
import com.noorazan.app.databinding.FragmentPrayerTimesBinding
import com.noorazan.app.scheduler.AzanScheduler
import com.noorazan.app.util.PrayTimeCalculator
import java.util.Calendar

class PrayerTimesFragment : Fragment() {

    private var _binding: FragmentPrayerTimesBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: PrayerPreferences
    private lateinit var adapter: PrayerTimesAdapter
    private var selectedCalendar: Calendar = Calendar.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPrayerTimesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = PrayerPreferences(requireContext())

        adapter = PrayerTimesAdapter { prayer, enabled ->
            prefs.setAzanEnabled(prayer, enabled)
            AzanScheduler.scheduleNextPrayerAlarm(requireContext())
            val state = if (enabled) "enabled" else "muted"
            Toast.makeText(requireContext(), "${prayer.displayName} Azan $state", Toast.LENGTH_SHORT).show()
            renderSchedule()
        }

        binding.recyclerPrayers.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerPrayers.adapter = adapter

        binding.btnPrevDay.setOnClickListener {
            selectedCalendar.add(Calendar.DAY_OF_YEAR, -1)
            renderSchedule()
        }

        binding.btnNextDay.setOnClickListener {
            selectedCalendar.add(Calendar.DAY_OF_YEAR, 1)
            renderSchedule()
        }

        binding.btnToday.setOnClickListener {
            selectedCalendar = Calendar.getInstance()
            renderSchedule()
        }
    }

    override fun onResume() {
        super.onResume()
        renderSchedule()
    }

    private fun renderSchedule() {
        val schedule = PrayTimeCalculator.getScheduleForDate(selectedCalendar, prefs)
        binding.tvScheduleDate.text = schedule.dateLabel
        binding.tvScheduleLocation.text = prefs.locationName
        adapter.submitList(schedule.items)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
