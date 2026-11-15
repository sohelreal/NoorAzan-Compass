package com.noorazan.app.util

import com.noorazan.app.data.PrayerPreferences
import com.noorazan.app.model.AsrJuristicMethod
import com.noorazan.app.model.CalculationMethod
import com.noorazan.app.model.DailyPrayerSchedule
import com.noorazan.app.model.PrayerName
import com.noorazan.app.model.PrayerTimeItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

object PrayTimeCalculator {

    fun getScheduleForDate(
        calendar: Calendar,
        prefs: PrayerPreferences,
        nowMillis: Long = System.currentTimeMillis()
    ): DailyPrayerSchedule {
        val lat = prefs.latitude
        val lng = prefs.longitude
        val method = prefs.calculationMethod
        val asrMethod = prefs.asrMethod

        val rawTimesHours = calculateRawHours(
            year = calendar.get(Calendar.YEAR),
            month = calendar.get(Calendar.MONTH) + 1,
            day = calendar.get(Calendar.DAY_OF_MONTH),
            latitude = lat,
            longitude = lng,
            timeZone = calendar.timeZone,
            dateForDst = calendar.time,
            method = method,
            asrMethod = asrMethod
        )

        val dayStart = (calendar.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val timeFormatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val dateFormatter = SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault())

        val rawItems = PrayerName.entries.map { prayer ->
            val baseHours = rawTimesHours[prayer] ?: 12.0
            val offsetMins = prefs.getManualOffsetMinutes(prayer)
            val totalMinutes = Math.round(baseHours * 60.0) + offsetMins
            val timestamp = dayStart + totalMinutes * 60_000L
            PrayerTimeItem(
                prayer = prayer,
                timestampMillis = timestamp,
                formattedTime = timeFormatter.format(Date(timestamp)),
                isEnabled = prefs.isAzanEnabled(prayer),
                isNext = false
            )
        }

        val nextUpcoming = rawItems.firstOrNull { it.timestampMillis > nowMillis }
        val finalItems = rawItems.map { item ->
            item.copy(isNext = (nextUpcoming != null && item.prayer == nextUpcoming.prayer))
        }

        return DailyPrayerSchedule(
            dateLabel = dateFormatter.format(calendar.time),
            items = finalItems,
            nextPrayer = finalItems.firstOrNull { it.isNext }
        )
    }

    fun getNextEnabledAzanPrayer(
        prefs: PrayerPreferences,
        nowMillis: Long = System.currentTimeMillis()
    ): PrayerTimeItem? {
        val today = Calendar.getInstance()
        val todaySchedule = getScheduleForDate(today, prefs, nowMillis)
        val todayNext = todaySchedule.items.firstOrNull {
            it.prayer.isAzanPrayer && it.isEnabled && it.timestampMillis > nowMillis + 1000L
        }
        if (todayNext != null) return todayNext

        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val tomorrowSchedule = getScheduleForDate(tomorrow, prefs, nowMillis)
        return tomorrowSchedule.items.firstOrNull {
            it.prayer.isAzanPrayer && it.isEnabled && it.timestampMillis > nowMillis + 1000L
        }
    }

    fun getNextPrayerForDisplay(
        prefs: PrayerPreferences,
        nowMillis: Long = System.currentTimeMillis()
    ): PrayerTimeItem? {
        val today = Calendar.getInstance()
        val todaySchedule = getScheduleForDate(today, prefs, nowMillis)
        val todayNext = todaySchedule.items.firstOrNull { it.timestampMillis > nowMillis }
        if (todayNext != null) return todayNext

        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val tomorrowSchedule = getScheduleForDate(tomorrow, prefs, nowMillis)
        return tomorrowSchedule.items.firstOrNull()
    }

    private fun calculateRawHours(
        year: Int,
        month: Int,
        day: Int,
        latitude: Double,
        longitude: Double,
        timeZone: TimeZone,
        dateForDst: Date,
        method: CalculationMethod,
        asrMethod: AsrJuristicMethod
    ): Map<PrayerName, Double> {
        val jd = julianDate(year, month, day) - longitude / (15.0 * 24.0)
        val tzOffsetHours = timeZone.getOffset(dateForDst.time) / 3_600_000.0

        val dhuhr = computeMidDay(jd + 0.5, longitude, tzOffsetHours)
        val sunrise = computeSunAngleTime(jd + 0.25, latitude, longitude, tzOffsetHours, 0.833, isMorning = true)
        val sunset = computeSunAngleTime(jd + 0.75, latitude, longitude, tzOffsetHours, 0.833, isMorning = false)

        val fajr = computeSunAngleTime(
            jd + 0.2,
            latitude,
            longitude,
            tzOffsetHours,
            method.fajrAngle,
            isMorning = true
        )

        val asr = computeAsrTime(
            jd + 0.625,
            latitude,
            longitude,
            tzOffsetHours,
            asrMethod.shadowFactor.toDouble()
        )

        val maghrib = sunset

        val isha = if (method.ishaMinutesAfterMaghrib != null) {
            maghrib + (method.ishaMinutesAfterMaghrib / 60.0)
        } else {
            computeSunAngleTime(
                jd + 0.8,
                latitude,
                longitude,
                tzOffsetHours,
                method.ishaAngle,
                isMorning = false
            )
        }

        val safeFajr = if (fajr.isNaN()) sunrise - 1.5 else fajr
        val safeIsha = if (isha.isNaN()) maghrib + 1.5 else isha

        return mapOf(
            PrayerName.FAJR to fixHour(safeFajr),
            PrayerName.SUNRISE to fixHour(sunrise),
            PrayerName.DHUHR to fixHour(dhuhr + 1.0 / 60.0),
            PrayerName.ASR to fixHour(asr),
            PrayerName.MAGHRIB to fixHour(maghrib),
            PrayerName.ISHA to fixHour(safeIsha)
        )
    }

    private fun julianDate(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    private fun sunPosition(jd: Double): Pair<Double, Double> {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * sinDeg(g) + 0.020 * sinDeg(2 * g))
        val e = 23.439 - 0.00000036 * d

        val ra = atan2Deg(cosDeg(e) * sinDeg(l), cosDeg(l)) / 15.0
        val declination = asinDeg(sinDeg(e) * sinDeg(l))
        val equationOfTime = q / 15.0 - fixHour(ra)
        return Pair(declination, equationOfTime)
    }

    private fun computeMidDay(jd: Double, longitude: Double, tzOffset: Double): Double {
        val (_, eqt) = sunPosition(jd)
        return fixHour(12.0 + tzOffset - (longitude / 15.0) - eqt)
    }

    private fun computeSunAngleTime(
        jd: Double,
        latitude: Double,
        longitude: Double,
        tzOffset: Double,
        angle: Double,
        isMorning: Boolean
    ): Double {
        val (decl, _) = sunPosition(jd)
        val noon = computeMidDay(jd, longitude, tzOffset)
        val numerator = -sinDeg(angle) - sinDeg(decl) * sinDeg(latitude)
        val denominator = cosDeg(decl) * cosDeg(latitude)
        val ratio = numerator / denominator
        if (ratio < -1.0 || ratio > 1.0) return Double.NaN
        val t = acosDeg(ratio) / 15.0
        return noon + if (isMorning) -t else t
    }

    private fun computeAsrTime(
        jd: Double,
        latitude: Double,
        longitude: Double,
        tzOffset: Double,
        shadowFactor: Double
    ): Double {
        val (decl, _) = sunPosition(jd)
        val angle = -acotDeg(shadowFactor + tanDeg(kotlin.math.abs(latitude - decl)))
        return computeSunAngleTime(jd, latitude, longitude, tzOffset, angle, isMorning = false)
    }

    private fun sinDeg(d: Double) = sin(Math.toRadians(d))
    private fun cosDeg(d: Double) = cos(Math.toRadians(d))
    private fun tanDeg(d: Double) = tan(Math.toRadians(d))
    private fun asinDeg(x: Double) = Math.toDegrees(asin(x))
    private fun acosDeg(x: Double) = Math.toDegrees(acos(x))
    private fun atan2Deg(y: Double, x: Double) = Math.toDegrees(atan2(y, x))
    private fun acotDeg(x: Double) = Math.toDegrees(atan(1.0 / x))

    private fun fixAngle(a: Double): Double {
        var res = a - 360.0 * floor(a / 360.0)
        if (res < 0) res += 360.0
        return res
    }

    private fun fixHour(h: Double): Double {
        var res = h - 24.0 * floor(h / 24.0)
        if (res < 0) res += 24.0
        return res
    }
}
