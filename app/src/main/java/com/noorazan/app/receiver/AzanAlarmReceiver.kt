package com.noorazan.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.noorazan.app.data.PrayerPreferences
import com.noorazan.app.model.PrayerName
import com.noorazan.app.scheduler.AzanScheduler
import com.noorazan.app.service.AzanPlaybackService

class AzanAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prayerStr = intent.getStringExtra(EXTRA_PRAYER_NAME) ?: return
        val prayer = runCatching { PrayerName.valueOf(prayerStr) }.getOrNull() ?: return

        val prefs = PrayerPreferences(context)
        if (prefs.isAzanEnabled(prayer)) {
            AzanPlaybackService.startAzan(context, prayer)
        }

        AzanScheduler.scheduleNextPrayerAlarm(context)
    }

    companion object {
        const val EXTRA_PRAYER_NAME = "extra_prayer_name"
    }
}
