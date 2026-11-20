package app.noor.prayer

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Real-time Qibla compass.
 * - Uses hardware-fused Rotation Vector (with Geomagnetic Rotation Vector and Accel+Mag fallbacks).
 * - Accounts for screen rotation and uses hysteresis between flat and upright holding so it never jumps.
 * - Uses shortest-angle adaptive smoothing for instant real-time tracking without jitter.
 * - Rotates the dial & needle on the GPU via graphicsLayer for smooth 60fps updates.
 */
@Composable
fun QiblaScreen() {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val azimuthState = remember { mutableFloatStateOf(0f) }
    var hasSensor by remember { mutableStateOf(true) }
    var gotReading by remember { mutableStateOf(false) }
    var accuracy by remember { mutableIntStateOf(SensorManager.SENSOR_STATUS_ACCURACY_HIGH) }
    var magInterference by remember { mutableStateOf(false) }
    var mode by rememberSaveable { mutableIntStateOf(0) } // 0 auto (rotation vector), 1 magnetometer + accelerometer
    val bearing = PrayerCalc.qiblaBearing(Prefs.lat, Prefs.lon).toFloat()

    // Try refreshing location automatically if not yet located and permission is already granted.
    LaunchedEffect(Prefs.located) {
        if (!Prefs.located && Loc.hasPermission(ctx)) {
            Loc.refresh(ctx)
        }
    }

    DisposableEffect(Prefs.lat, Prefs.lon, mode) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rvPrimary = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sm.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        val acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val mag = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val useAlt = mode == 1 && acc != null && mag != null
        val rv = if (useAlt) null else rvPrimary

        if (rv == null && (acc == null || mag == null)) {
            hasSensor = false
            onDispose { }
        } else {
            hasSensor = true
            val decl = GeomagneticField(
                Prefs.lat.toFloat(),
                Prefs.lon.toFloat(),
                0f,
                System.currentTimeMillis()
            ).declination

            var smoothAz = 0f
            var initialized = false
            var upright = false

            val rot = FloatArray(9)
            val remapped = FloatArray(9)
            val screenRemapped = FloatArray(9)
            val ori = FloatArray(3)
            val gravity = FloatArray(3)
            val geo = FloatArray(3)
            var haveG = false
            var haveM = false

            fun displayRotation(): Int = runCatching {
                if (Build.VERSION.SDK_INT >= 30) {
                    ctx.display?.rotation ?: Surface.ROTATION_0
                } else {
                    @Suppress("DEPRECATION")
                    (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
                }
            }.getOrDefault(Surface.ROTATION_0)

            fun updateFromRotationMatrix(isFused: Boolean) {
                // Hysteresis prevents sudden jumps when tilting around ~50 degrees.
                val z = abs(rot[8])
                if (!upright && z < 0.45f) upright = true
                else if (upright && z > 0.65f) upright = false

                val base = if (!upright) {
                    rot
                } else {
                    SensorManager.remapCoordinateSystem(rot, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapped)
                    remapped
                }

                // Compensate for current screen rotation.
                val finalMatrix = when (displayRotation()) {
                    Surface.ROTATION_90 -> {
                        SensorManager.remapCoordinateSystem(base, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, screenRemapped)
                        screenRemapped
                    }
                    Surface.ROTATION_180 -> {
                        SensorManager.remapCoordinateSystem(base, SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y, screenRemapped)
                        screenRemapped
                    }
                    Surface.ROTATION_270 -> {
                        SensorManager.remapCoordinateSystem(base, SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X, screenRemapped)
                        screenRemapped
                    }
                    else -> base
                }

                SensorManager.getOrientation(finalMatrix, ori)
                val rawAz = ((Math.toDegrees(ori[0].toDouble()).toFloat() + decl + Prefs.compassOffset) % 360f + 360f) % 360f

                if (!initialized) {
                    smoothAz = rawAz
                    initialized = true
                } else {
                    // Shortest signed angular difference in (-180, 180].
                    val delta = ((rawAz - smoothAz + 540f) % 360f + 360f) % 360f - 180f
                    val absDelta = abs(delta)
                    // Adaptive alpha: instant response when turning, smooth damping when holding steady.
                    val alpha = when {
                        isFused && absDelta > 10f -> 0.60f
                        isFused && absDelta > 3f -> 0.38f
                        isFused -> 0.22f
                        absDelta > 10f -> 0.35f
                        else -> 0.16f
                    }
                    smoothAz = ((smoothAz + alpha * delta) % 360f + 360f) % 360f
                }

                azimuthState.floatValue = smoothAz
                gotReading = true
            }

            val listener = object : SensorEventListener {
                override fun onSensorChanged(e: SensorEvent) {
                    when (e.sensor.type) {
                        Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> {
                            // Some OEMs return >4 elements; truncate safely to 4 for getRotationMatrixFromVector.
                            val vals = if (e.values.size > 4) e.values.copyOf(4) else e.values
                            SensorManager.getRotationMatrixFromVector(rot, vals)
                            updateFromRotationMatrix(isFused = true)
                        }
                        Sensor.TYPE_ACCELEROMETER -> {
                            for (i in 0..2) {
                                gravity[i] = if (haveG) gravity[i] * 0.80f + e.values[i] * 0.20f else e.values[i]
                            }
                            haveG = true
                        }
                        Sensor.TYPE_MAGNETIC_FIELD -> {
                            val mx = e.values[0]
                            val my = e.values[1]
                            val mz = e.values[2]
                            val fieldStrength = sqrt(mx * mx + my * my + mz * mz)
                            // Normal Earth magnetic field is ~25 to 65 uT.
                            magInterference = fieldStrength < 18f || fieldStrength > 75f

                            if (rv == null) {
                                for (i in 0..2) {
                                    geo[i] = if (haveM) geo[i] * 0.75f + e.values[i] * 0.25f else e.values[i]
                                }
                                haveM = true
                                if (haveG && SensorManager.getRotationMatrix(rot, null, gravity, geo)) {
                                    updateFromRotationMatrix(isFused = false)
                                }
                            }
                        }
                    }
                }

                override fun onAccuracyChanged(s: Sensor?, a: Int) {
                    if (s?.type == Sensor.TYPE_MAGNETIC_FIELD ||
                        s?.type == Sensor.TYPE_ROTATION_VECTOR ||
                        s?.type == Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR
                    ) {
                        accuracy = a
                    }
                }
            }

            if (rv != null) {
                sm.registerListener(listener, rv, SensorManager.SENSOR_DELAY_GAME)
                // Also monitor magnetometer at low rate for accurate calibration & interference warnings.
                if (mag != null) {
                    sm.registerListener(listener, mag, SensorManager.SENSOR_DELAY_UI)
                }
            } else {
                sm.registerListener(listener, acc, SensorManager.SENSOR_DELAY_GAME)
                sm.registerListener(listener, mag, SensorManager.SENSOR_DELAY_GAME)
            }
            onDispose { sm.unregisterListener(listener) }
        }
    }

    val azimuth = azimuthState.floatValue
    val needle = bearing - azimuth // degrees the Qibla is clockwise from the top of the phone
    val diff = ((needle + 540f) % 360f + 360f) % 360f - 180f
    val aligned = hasSensor && gotReading && abs(diff) < 3.5f
    LaunchedEffect(aligned) { if (aligned) haptic.performHapticFeedback(HapticFeedbackType.LongPress) }

    val ring = MaterialTheme.colorScheme.primary
    val ringSoft = MaterialTheme.colorScheme.primaryContainer
    val textColor = MaterialTheme.colorScheme.onSurface

    Column(Modifier.fillMaxSize()) {
        Header(tr("qibla"))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(tr("qibla_dir"), style = MaterialTheme.typography.titleMedium)
            Text(
                nd("%.1f°".format(java.util.Locale.ENGLISH, bearing)) + " " + tr("from_north"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = ring
            )
            Spacer(Modifier.height(12.dp))
            Text("▼", color = if (aligned) ring else MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.titleLarge)

            QiblaDial(
                azimuthState = azimuthState,
                bearing = bearing,
                aligned = aligned,
                ring = ring,
                ringSoft = ringSoft,
                textColor = textColor
            )

            Spacer(Modifier.height(16.dp))
            if (!hasSensor) {
                Text(tr("no_sensor"), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            } else {
                val statusText = when {
                    aligned -> "✓ " + tr("facing")
                    diff > 0 -> "↻ " + tr("turn_right") + " " + nd("%.0f°".format(java.util.Locale.ENGLISH, abs(diff)))
                    else -> "↺ " + tr("turn_left") + " " + nd("%.0f°".format(java.util.Locale.ENGLISH, abs(diff)))
                }
                Text(
                    statusText,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (aligned) ring else textColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    tr("heading") + " " + nd("%.0f°".format(java.util.Locale.ENGLISH, azimuth)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (hasSensor && (accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW || magInterference)) {
                Spacer(Modifier.height(8.dp))
                Text(
                    if (magInterference) tr("magnetic_interference") else tr("calibrate"),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text(tr("compass_auto")) })
                FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text(tr("compass_alt")) })
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    tr("compass_fix") + ": " + nd((if (Prefs.compassOffset > 0) "+" else "") + "${Prefs.compassOffset}°"),
                    style = MaterialTheme.typography.labelLarge
                )
                if (Prefs.compassOffset != 0) {
                    TextButton(onClick = { Prefs.update { compassOffset = 0 } }) {
                        Text(tr("reset"))
                    }
                }
            }
            Slider(
                value = Prefs.compassOffset.toFloat(),
                onValueChange = { v -> Prefs.update { compassOffset = v.toInt() } },
                valueRange = -180f..180f
            )
            Text(tr("compass_fix_hint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(tr("keep_flat"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            if (!Prefs.located) {
                Spacer(Modifier.height(8.dp))
                Text(tr("loc_not_set"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                if (Loc.hasPermission(ctx)) {
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = { Loc.refresh(ctx) }) { Text(tr("detect")) }
                }
            }
        }
    }
}

@Composable
private fun QiblaDial(
    azimuthState: State<Float>,
    bearing: Float,
    aligned: Boolean,
    ring: Color,
    ringSoft: Color,
    textColor: Color
) {
    Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxSize()
                .background(if (aligned) ring.copy(alpha = 0.25f) else ringSoft, CircleShape)
                .border(if (aligned) 6.dp else 3.dp, ring, CircleShape)
        )
        // True north compass ring (GPU-rotated via graphicsLayer for smooth 60fps real-time motion).
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                rotationZ = -azimuthState.value
            }
        ) {
            Text("N", Modifier.align(Alignment.TopCenter).padding(top = 6.dp), fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
            Text("S", Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp), fontWeight = FontWeight.Bold, color = textColor)
            Text("E", Modifier.align(Alignment.CenterEnd).padding(end = 10.dp), fontWeight = FontWeight.Bold, color = textColor)
            Text("W", Modifier.align(Alignment.CenterStart).padding(start = 10.dp), fontWeight = FontWeight.Bold, color = textColor)
        }
        // Qibla needle (GPU-rotated via graphicsLayer).
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                rotationZ = bearing - azimuthState.value
            }
        ) {
            Column(Modifier.align(Alignment.TopCenter).padding(top = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🕋", fontSize = 36.sp)
                Box(Modifier.size(width = 6.dp, height = 100.dp).background(ring, RoundedCornerShape(3.dp)))
            }
        }
        Box(Modifier.size(14.dp).background(ring, CircleShape))
    }
}
