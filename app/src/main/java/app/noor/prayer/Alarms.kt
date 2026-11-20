package app.noor.prayer

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import java.time.LocalDate

/**
 * Shared wake lock used to bridge the gap between AzanReceiver.onReceive() and
 * PlayerService.onStartCommand() so OEM devices (Samsung, Xiaomi, Oppo, Vivo, OnePlus)
 * cannot put the CPU back to sleep before full Azan playback begins.
 */
object AzanWakeLock {
    private var lock: PowerManager.WakeLock? = null

    @Synchronized
    fun acquire(ctx: Context, timeoutMs: Long = 30_000L) {
        release()
        val pm = ctx.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        lock = runCatching {
            pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "noor:azan_handoff").apply {
                setReferenceCounted(false)
                acquire(timeoutMs)
            }
        }.getOrNull()
    }

    @Synchronized
    fun release() {
        runCatching {
            lock?.let { if (it.isHeld) it.release() }
        }
        lock = null
    }
}

/**
 * Schedules exact alarms for all 5 upcoming enabled prayers (Fajr..Isha) using setAlarmClock()
 * whenever permitted, so Doze mode and OEM battery killers wake the device on time and allow
 * starting the full-Azan foreground service. Each fired alarm also refreshes the full schedule.
 */
object Alarms {
    private const val REQ_BASE = 100
    private const val REQ_TEST = 199

    private fun scheduleExact(app: Context, am: AlarmManager, reqCode: Int, triggerAtMs: Long, pi: PendingIntent) {
        val showIntent = PendingIntent.getActivity(
            app,
            reqCode + 50,
            Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        try {
            if (canExact) {
                // setAlarmClock() is exempt from Doze and grants background FGS launch permission on Android 12-16.
                am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMs, showIntent), pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
            }
        } catch (e: SecurityException) {
            runCatching { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi) }
                .onFailure { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi) }
        }
    }

    fun scheduleNext(ctx: Context) {
        val app = ctx.applicationContext
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()
        val today = LocalDate.now()

        // Schedule the next occurrence of each of the 5 prayers independently.
        for (p in 0..4) {
            val intent = Intent(app, AzanReceiver::class.java).putExtra("idx", p)
            if (!Prefs.notify[p]) {
                val existing = PendingIntent.getBroadcast(
                    app,
                    REQ_BASE + p,
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                existing?.let { am.cancel(it) }
                continue
            }

            var triggerMs: Long? = null
            for (d in 0..2) {
                val day = PrayerCalc.day(today.plusDays(d.toLong())) ?: continue
                val ms = day.prayer(p)
                if (ms > now + 1000L) {
                    triggerMs = ms
                    break
                }
            }

            if (triggerMs == null) {
                val existing = PendingIntent.getBroadcast(
                    app,
                    REQ_BASE + p,
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                existing?.let { am.cancel(it) }
                continue
            }

            val pi = PendingIntent.getBroadcast(
                app,
                REQ_BASE + p,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            scheduleExact(app, am, REQ_BASE + p, triggerMs, pi)
        }
    }

    /** Schedules a real background alarm 15 seconds from now so the user can lock the screen and verify full Azan. */
    fun scheduleTest(ctx: Context, delaySec: Int = 15) {
        val app = ctx.applicationContext
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(app, AzanReceiver::class.java)
            .putExtra("idx", 1)
            .putExtra("isTest", true)
        val pi = PendingIntent.getBroadcast(
            app,
            REQ_TEST,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleExact(app, am, REQ_TEST, System.currentTimeMillis() + delaySec * 1000L, pi)
    }
}

object Notifier {
    private fun openApp(ctx: Context): PendingIntent =
        PendingIntent.getActivity(
            ctx,
            0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun azanText(idx: Int): Pair<String, String> {
        val jumuah = java.time.LocalDate.now().dayOfWeek == java.time.DayOfWeek.FRIDAY
        val name = prayerName(idx, jumuah)
        return name to tr("its_time").replace("%s", name)
    }

    fun postAzan(ctx: Context, idx: Int, channel: String) {
        val (title, text) = azanText(idx)
        val n = Notification.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
        runCatching { (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(200 + idx, n) }
    }

    fun player(ctx: Context, title: String, text: String, quran: Boolean, playing: Boolean): Notification {
        fun action(act: String, req: Int): PendingIntent =
            PendingIntent.getService(ctx, req, Intent(ctx, PlayerService::class.java).setAction(act), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val channelId = if (quran) "player" else "azan_playing"
        val b = Notification.Builder(ctx, channelId)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp(ctx))
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
        if (!quran) {
            b.setCategory(Notification.CATEGORY_ALARM)
        }
        if (quran) {
            b.addAction(Notification.Action.Builder(null, if (playing) tr("pause") else tr("resume"), action(PlayerService.ACTION_TOGGLE, 1)).build())
        }
        b.addAction(Notification.Action.Builder(null, tr("stop"), action(PlayerService.ACTION_STOP, 2)).build())
        return b.build()
    }
}

class AzanReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        Prefs.init(app)
        val idx = intent.getIntExtra("idx", 0).coerceIn(0, 4)
        val isTest = intent.getBooleanExtra("isTest", false)
        // Acquire handoff wake lock; PlayerService releases it once its own playback wake lock is active.
        AzanWakeLock.acquire(app, 30_000L)
        var startedService = false
        try {
            if (Prefs.azanMode == 0 || isTest) {
                try {
                    val i = Intent(app, PlayerService::class.java)
                        .setAction(PlayerService.ACTION_AZAN)
                        .putExtra("idx", idx)
                    app.startForegroundService(i)
                    startedService = true
                } catch (e: Exception) {
                    Notifier.postAzan(app, idx, "azan_alert")
                }
            } else {
                Notifier.postAzan(app, idx, if (Prefs.azanMode == 1) "azan_alert" else "azan_quiet")
            }
        } finally {
            if (!isTest) {
                Alarms.scheduleNext(app)
            }
            if (!startedService) {
                AzanWakeLock.release()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Prefs.init(context.applicationContext)
        Alarms.scheduleNext(context)
    }
}
