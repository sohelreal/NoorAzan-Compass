package com.noorazan.app.model

enum class PrayerName(
    val displayName: String,
    val arabicName: String,
    val isAzanPrayer: Boolean
) {
    FAJR("Fajr", "الفجر", true),
    SUNRISE("Sunrise", "الشروق", false),
    DHUHR("Dhuhr", "الظهر", true),
    ASR("Asr", "العصر", true),
    MAGHRIB("Maghrib", "المغرب", true),
    ISHA("Isha", "العشاء", true);

    companion object {
        fun azanPrayers(): List<PrayerName> = entries.filter { it.isAzanPrayer }
    }
}

enum class CalculationMethod(
    val id: Int,
    val title: String,
    val subtitle: String,
    val fajrAngle: Double,
    val ishaAngle: Double,
    val ishaMinutesAfterMaghrib: Int? = null
) {
    MWL(0, "Muslim World League (MWL)", "Fajr 18° • Isha 17°", 18.0, 17.0),
    ISNA(1, "Islamic Society of North America (ISNA)", "Fajr 15° • Isha 15°", 15.0, 15.0),
    EGYPT(2, "Egyptian General Authority of Survey", "Fajr 19.5° • Isha 17.5°", 19.5, 17.5),
    MAKKAH(3, "Umm al-Qura University, Makkah", "Fajr 18.5° • Isha 90 min after Maghrib", 18.5, 0.0, 90),
    KARACHI(4, "University of Islamic Sciences, Karachi", "Fajr 18° • Isha 18°", 18.0, 18.0),
    TEHRAN(5, "Institute of Geophysics, Tehran", "Fajr 17.7° • Isha 14°", 17.7, 14.0);

    companion object {
        fun fromId(id: Int): CalculationMethod = entries.firstOrNull { it.id == id } ?: MWL
    }
}

enum class AsrJuristicMethod(
    val id: Int,
    val title: String,
    val shadowFactor: Int
) {
    STANDARD(0, "Standard (Shafi'i, Maliki, Hanbali)", 1),
    HANAFI(1, "Hanafi", 2);

    companion object {
        fun fromId(id: Int): AsrJuristicMethod = entries.firstOrNull { it.id == id } ?: STANDARD
    }
}

data class PrayerTimeItem(
    val prayer: PrayerName,
    val timestampMillis: Long,
    val formattedTime: String,
    val isEnabled: Boolean,
    val isNext: Boolean = false
)

data class DailyPrayerSchedule(
    val dateLabel: String,
    val items: List<PrayerTimeItem>,
    val nextPrayer: PrayerTimeItem?
)
