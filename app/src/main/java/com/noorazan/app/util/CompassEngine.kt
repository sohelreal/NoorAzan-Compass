package com.noorazan.app.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt

/** One compass sample. Heading is relative to MAGNETIC north (add declination for true north). */
data class CompassReading(
    val magneticHeading: Float,
    /** Estimated heading error in degrees, or -1 if the device does not report it. */
    val accuracyDeg: Float,
    /** Magnetic field strength in microtesla, or 0 if unknown. Normal Earth field is ~25-65. */
    val fieldMicroTesla: Float,
    /** SensorManager.SENSOR_STATUS_* of the magnetometer. */
    val calibration: Int
)

/**
 * Fast, tilt-compensated compass.
 * - Preferred: fused ROTATION_VECTOR sensor at ~50 Hz.
 * - Fallback: accelerometer + magnetometer with a light low-pass filter.
 * Heading uses the horizontal projection of the device-top and device-back axes, so it is
 * correct when the phone is flat, tilted, or held upright.
 */
class CompassEngine(
    context: Context,
    private val onReading: (CompassReading) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    val isAvailable: Boolean
        get() = rotationSensor != null || (accelSensor != null && magSensor != null)

    private val rotationVector = FloatArray(4)
    private val matrix = FloatArray(9)
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var hasGravity = false
    private var hasGeomagnetic = false
    private var fieldMicroTesla = 0f
    private var calibration = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
    private var running = false

    fun start() {
        if (running) return
        running = true
        if (rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SAMPLING_US)
            if (magSensor != null) {
                sensorManager.registerListener(this, magSensor, SensorManager.SENSOR_DELAY_NORMAL)
            }
        } else if (accelSensor != null && magSensor != null) {
            sensorManager.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_GAME)
            sensorManager.registerListener(this, magSensor, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        running = false
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                val n = min(4, event.values.size)
                System.arraycopy(event.values, 0, rotationVector, 0, n)
                SensorManager.getRotationMatrixFromVector(matrix, rotationVector)
                var accuracy = -1f
                if (event.values.size >= 5 && event.values[4] >= 0f) {
                    accuracy = Math.toDegrees(event.values[4].toDouble()).toFloat()
                }
                emit(accuracy)
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                fieldMicroTesla = sqrt(x * x + y * y + z * z)
                if (rotationSensor == null) {
                    for (i in 0..2) {
                        geomagnetic[i] = if (hasGeomagnetic) {
                            LOW_PASS * geomagnetic[i] + (1f - LOW_PASS) * event.values[i]
                        } else {
                            event.values[i]
                        }
                    }
                    hasGeomagnetic = true
                    computeFromAccelMag()
                }
            }
            Sensor.TYPE_ACCELEROMETER -> {
                if (rotationSensor == null) {
                    for (i in 0..2) {
                        gravity[i] = if (hasGravity) {
                            LOW_PASS * gravity[i] + (1f - LOW_PASS) * event.values[i]
                        } else {
                            event.values[i]
                        }
                    }
                    hasGravity = true
                }
            }
        }
    }

    private fun computeFromAccelMag() {
        if (!hasGravity || !hasGeomagnetic) return
        if (SensorManager.getRotationMatrix(matrix, null, gravity, geomagnetic)) {
            emit(-1f)
        }
    }

    private fun emit(accuracyDeg: Float) {
        // matrix maps device coords -> world coords (x=East, y=North, z=Up)
        val east = (matrix[1] - matrix[2]).toDouble()
        val north = (matrix[4] - matrix[5]).toDouble()
        var heading = Math.toDegrees(atan2(east, north)).toFloat()
        heading = (heading + 360f) % 360f
        onReading(CompassReading(heading, accuracyDeg, fieldMicroTesla, calibration))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) calibration = accuracy
    }

    private companion object {
        const val SAMPLING_US = 20_000   // ~50 Hz
        const val LOW_PASS = 0.8f
    }
}
