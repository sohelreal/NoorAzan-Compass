package com.noorazan.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import com.noorazan.app.model.PrayerName
import com.noorazan.app.util.NotificationHelper

class AzanPlaybackService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_AZAN -> {
                stopPlaybackAndService()
                return START_NOT_STICKY
            }
            ACTION_PLAY_AZAN -> {
                val prayerStr = intent.getStringExtra(EXTRA_PRAYER_NAME) ?: PrayerName.DHUHR.name
                val prayer = runCatching { PrayerName.valueOf(prayerStr) }.getOrDefault(PrayerName.DHUHR)
                startForegroundWithNotification(prayer)
                acquireWakeLock()
                playAzanAudio(prayer)
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification(prayer: PrayerName) {
        NotificationHelper.createNotificationChannels(this)
        val notification = NotificationHelper.buildAzanNotification(this, prayer)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NotificationHelper.NOTIFICATION_ID_AZAN,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID_AZAN, notification)
        }
    }

    private fun acquireWakeLock() {
        releaseWakeLock()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "NoorAzan::AzanPlaybackWakeLock"
        ).apply {
            acquire(10 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {
        } finally {
            wakeLock = null
        }
    }

    private fun playAzanAudio(prayer: PrayerName) {
        releaseMediaPlayer()

        val assetPath = if (prayer == PrayerName.FAJR) {
            "azan/fajr.mp3"
        } else {
            "azan/azan.mp3"
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        try {
            val afd = assets.openFd(assetPath)
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(audioAttributes)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                setOnCompletionListener {
                    stopPlaybackAndService()
                }
                setOnErrorListener { _, _, _ ->
                    stopPlaybackAndService()
                    true
                }
                prepare()
                start()
            }
        } catch (_: Exception) {
            try {
                val fallbackUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(audioAttributes)
                    setDataSource(applicationContext, fallbackUri)
                    isLooping = false
                    setOnCompletionListener {
                        stopPlaybackAndService()
                    }
                    prepare()
                    start()
                }
            } catch (_: Exception) {
                stopPlaybackAndService()
            }
        }
    }

    private fun releaseMediaPlayer() {
        try {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) mp.stop()
                mp.reset()
                mp.release()
            }
        } catch (_: Exception) {
        } finally {
            mediaPlayer = null
        }
    }

    private fun stopPlaybackAndService() {
        releaseMediaPlayer()
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        releaseMediaPlayer()
        releaseWakeLock()
        super.onDestroy()
    }

    companion object {
        const val ACTION_PLAY_AZAN = "com.noorazan.app.action.PLAY_AZAN"
        const val ACTION_STOP_AZAN = "com.noorazan.app.action.STOP_AZAN"
        const val EXTRA_PRAYER_NAME = "extra_prayer_name"

        fun startAzan(context: Context, prayer: PrayerName) {
            val intent = Intent(context, AzanPlaybackService::class.java).apply {
                action = ACTION_PLAY_AZAN
                putExtra(EXTRA_PRAYER_NAME, prayer.name)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopAzan(context: Context) {
            val intent = Intent(context, AzanPlaybackService::class.java).apply {
                action = ACTION_STOP_AZAN
            }
            context.startService(intent)
        }
    }
}
