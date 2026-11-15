package com.noorazan.app

import android.app.Application
import com.noorazan.app.scheduler.AzanScheduler
import com.noorazan.app.util.NotificationHelper
import com.noorazan.app.worker.DailyPrayerUpdateWorker

class NoorAzanApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannels(this)
        AzanScheduler.scheduleNextPrayerAlarm(this)
        DailyPrayerUpdateWorker.enqueuePeriodicWork(this)
    }
}
