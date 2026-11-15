package com.noorazan.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.noorazan.app.scheduler.AzanScheduler
import com.noorazan.app.util.LocationHelper

class BootAndTimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                AzanScheduler.scheduleNextPrayerAlarm(context)
                LocationHelper.registerSignificantLocationUpdates(context)
            }
        }
    }
}
