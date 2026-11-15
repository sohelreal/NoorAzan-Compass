package com.noorazan.app.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.noorazan.app.data.PrayerPreferences
import com.noorazan.app.receiver.LocationUpdateReceiver
import com.noorazan.app.scheduler.AzanScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

object LocationHelper {

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    suspend fun fetchAndSaveCurrentLocation(context: Context): Boolean {
        if (!hasLocationPermission(context)) return false

        val client = LocationServices.getFusedLocationProviderClient(context)
        val location: Location? = suspendCancellableCoroutine { cont ->
            val cts = CancellationTokenSource()
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        cont.resume(loc)
                    } else {
                        client.lastLocation
                            .addOnSuccessListener { last -> cont.resume(last) }
                            .addOnFailureListener { cont.resume(null) }
                    }
                }
                .addOnFailureListener {
                    client.lastLocation
                        .addOnSuccessListener { last -> cont.resume(last) }
                        .addOnFailureListener { cont.resume(null) }
                }
        }

        if (location != null) {
            saveNewLocation(context, location.latitude, location.longitude)
            return true
        }
        return false
    }

    suspend fun saveNewLocation(context: Context, latitude: Double, longitude: Double) {
        val prefs = PrayerPreferences(context)
        prefs.latitude = latitude
        prefs.longitude = longitude
        prefs.hasLocationFix = true

        val city = resolveCityName(context, latitude, longitude)
        prefs.locationName = city

        AzanScheduler.scheduleNextPrayerAlarm(context)
    }

    @Suppress("DEPRECATION")
    private suspend fun resolveCityName(
        context: Context,
        latitude: Double,
        longitude: Double
    ): String = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            val addr = addresses?.firstOrNull()
            val city = addr?.locality ?: addr?.subAdminArea ?: addr?.adminArea
            val country = addr?.countryName
            when {
                !city.isNullOrBlank() && !country.isNullOrBlank() -> "$city, $country"
                !city.isNullOrBlank() -> city
                else -> String.format(Locale.US, "%.4f°, %.4f°", latitude, longitude)
            }
        } catch (_: Exception) {
            String.format(Locale.US, "%.4f°, %.4f°", latitude, longitude)
        }
    }

    @SuppressLint("MissingPermission")
    fun registerSignificantLocationUpdates(context: Context) {
        if (!hasLocationPermission(context)) return

        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(
            Priority.PRIORITY_PASSIVE,
            30 * 60 * 1000L
        )
            .setMinUpdateDistanceMeters(5000f)
            .setMinUpdateIntervalMillis(15 * 60 * 1000L)
            .build()

        val intent = Intent(context, LocationUpdateReceiver::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(context, 404, intent, flags)
        client.requestLocationUpdates(request, pendingIntent)
    }
}
