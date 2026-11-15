package com.noorazan.app.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.noorazan.app.data.PrayerPreferences
import com.noorazan.app.receiver.AzanAlarmReceiver
import com.noorazan.app.ui.MainActivity
import com.noorazan.app.util.PrayTimeCalculator

object AzanScheduler {

    private const val REQUEST_CODE_NEXT_AZAN = 2001

    fun scheduleNextPrayerAlarm(context: Context) {
        val prefs = PrayerPreferences(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        cancelScheduledAlarm(context)

        val nextPrayer = PrayTimeCalculator.getNextEnabledAzanPrayer(prefs) ?: return

        val alarmIntent = Intent(context, AzanAlarmReceiver::class.java).apply {
            putExtra(AzanAlarmReceiver.EXTRA_PRAYER_NAME, nextPrayer.prayer.name)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_NEXT_AZAN,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = PendingIntent.getActivity(
            context,
            2002,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextPrayer.timestampMillis,
                    pendingIntent
                )
            } else {
                val alarmClockInfo = AlarmManager.AlarmClockInfo(
                    nextPrayer.timestampMillis,
                    showIntent
                )
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextPrayer.timestampMillis,
                pendingIntent
            )
        }
    }

    fun cancelScheduledAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AzanAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_NEXT_AZAN,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}
