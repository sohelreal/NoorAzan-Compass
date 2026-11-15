package com.noorazan.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.noorazan.app.scheduler.AzanScheduler
import com.noorazan.app.util.LocationHelper
import java.util.concurrent.TimeUnit

class DailyPrayerUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        LocationHelper.fetchAndSaveCurrentLocation(applicationContext)
        AzanScheduler.scheduleNextPrayerAlarm(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "daily_prayer_schedule_refresh"

        fun enqueuePeriodicWork(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailyPrayerUpdateWorker>(6, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
