package com.noorazan.app.util

import android.hardware.GeomagneticField
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object QiblaCalculator {
    const val KAABA_LAT = 21.422487
    const val KAABA_LNG = 39.826206

    /**
     * Calculates the Qibla direction in degrees clockwise from True North (0° - 360°).
     */
    fun calculateQiblaBearing(latitude: Double, longitude: Double): Float {
        val phiK = Math.toRadians(KAABA_LAT)
        val lambdaK = Math.toRadians(KAABA_LNG)
        val phi = Math.toRadians(latitude)
        val lambda = Math.toRadians(longitude)

        val deltaLambda = lambdaK - lambda
        val y = sin(deltaLambda) * cos(phiK)
        val x = cos(phi) * sin(phiK) - sin(phi) * cos(phiK) * cos(deltaLambda)

        val bearing = Math.toDegrees(atan2(y, x))
        return ((bearing + 360.0) % 360.0).toFloat()
    }

    /**
     * Returns the magnetic declination for the given location and current time,
     * allowing the magnetic sensor reading to be adjusted to True North.
     */
    fun getMagneticDeclination(latitude: Double, longitude: Double): Float {
        return try {
            val geoField = GeomagneticField(
                latitude.toFloat(),
                longitude.toFloat(),
                0f,
                System.currentTimeMillis()
            )
            geoField.declination
        } catch (_: Exception) {
            0f
        }
    }
}
