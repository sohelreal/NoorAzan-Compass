package com.noorazan.app.data

import android.content.Context
import android.content.SharedPreferences
import com.noorazan.app.model.AsrJuristicMethod
import com.noorazan.app.model.CalculationMethod
import com.noorazan.app.model.PrayerName

class PrayerPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("noor_azan_prefs", Context.MODE_PRIVATE)

    var latitude: Double
        get() = java.lang.Double.longBitsToDouble(
            prefs.getLong(KEY_LATITUDE, java.lang.Double.doubleToRawLongBits(21.4225))
        )
        set(value) = prefs.edit()
            .putLong(KEY_LATITUDE, java.lang.Double.doubleToRawLongBits(value))
            .apply()

    var longitude: Double
        get() = java.lang.Double.longBitsToDouble(
            prefs.getLong(KEY_LONGITUDE, java.lang.Double.doubleToRawLongBits(39.8262))
        )
        set(value) = prefs.edit()
            .putLong(KEY_LONGITUDE, java.lang.Double.doubleToRawLongBits(value))
            .apply()

    var hasLocationFix: Boolean
        get() = prefs.getBoolean(KEY_HAS_LOCATION, false)
        set(value) = prefs.edit().putBoolean(KEY_HAS_LOCATION, value).apply()

    var locationName: String
        get() = prefs.getString(KEY_LOCATION_NAME, "Tap to detect GPS location") ?: "Unknown Location"
        set(value) = prefs.edit().putString(KEY_LOCATION_NAME, value).apply()

    var calculationMethod: CalculationMethod
        get() = CalculationMethod.fromId(prefs.getInt(KEY_CALC_METHOD, CalculationMethod.MWL.id))
        set(value) = prefs.edit().putInt(KEY_CALC_METHOD, value.id).apply()

    var asrMethod: AsrJuristicMethod
        get() = AsrJuristicMethod.fromId(prefs.getInt(KEY_ASR_METHOD, AsrJuristicMethod.STANDARD.id))
        set(value) = prefs.edit().putInt(KEY_ASR_METHOD, value.id).apply()

    fun isAzanEnabled(prayer: PrayerName): Boolean {
        if (!prayer.isAzanPrayer) return false
        return prefs.getBoolean("azan_enabled_${prayer.name}", true)
    }

    fun setAzanEnabled(prayer: PrayerName, enabled: Boolean) {
        prefs.edit().putBoolean("azan_enabled_${prayer.name}", enabled).apply()
    }

    fun getManualOffsetMinutes(prayer: PrayerName): Int {
        return prefs.getInt("offset_${prayer.name}", 0)
    }

    fun setManualOffsetMinutes(prayer: PrayerName, minutes: Int) {
        prefs.edit().putInt("offset_${prayer.name}", minutes.coerceIn(-60, 60)).apply()
    }

    companion object {
        private const val KEY_LATITUDE = "latitude"
        private const val KEY_LONGITUDE = "longitude"
        private const val KEY_HAS_LOCATION = "has_location"
        private const val KEY_LOCATION_NAME = "location_name"
        private const val KEY_CALC_METHOD = "calc_method"
        private const val KEY_ASR_METHOD = "asr_method"
    }
}
