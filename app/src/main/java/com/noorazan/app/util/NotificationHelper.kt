package com.noorazan.app.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.noorazan.app.R
import com.noorazan.app.model.PrayerName
import com.noorazan.app.service.AzanPlaybackService
import com.noorazan.app.ui.MainActivity

object NotificationHelper {

    const val CHANNEL_AZAN_PLAYBACK = "azan_playback_channel"
    const val NOTIFICATION_ID_AZAN = 1001

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return

            val azanChannel = NotificationChannel(
                CHANNEL_AZAN_PLAYBACK,
                "Azan Prayer Call",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Displays active prayer notification while Azan is playing"
                setSound(null, null)
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(azanChannel)
        }
    }

    fun buildAzanNotification(context: Context, prayer: PrayerName): Notification {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            10,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(context, AzanPlaybackService::class.java).apply {
            action = AzanPlaybackService.ACTION_STOP_AZAN
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            11,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_AZAN_PLAYBACK)
            .setSmallIcon(R.drawable.ic_mosque)
            .setContentTitle("${prayer.displayName} Azan • ${prayer.arabicName}")
            .setContentText("It is time for ${prayer.displayName} prayer. Tap Stop to silence Azan.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(
                R.drawable.ic_stop,
                "Stop Azan",
                stopPendingIntent
            )
            .build()
    }
}
