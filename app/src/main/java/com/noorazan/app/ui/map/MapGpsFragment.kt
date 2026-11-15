package com.noorazan.app.ui.map

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.DashPathEffect
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.noorazan.app.R
import com.noorazan.app.databinding.FragmentMapGpsBinding
import com.noorazan.app.util.LocationHelper
import com.noorazan.app.util.QiblaCalculator
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Live GPS details + OpenStreetMap map with a great-circle line to the Kaaba. */
class MapGpsFragment : Fragment() {

    private var _binding: FragmentMapGpsBinding? = null
    private val binding get() = _binding!!

    private lateinit var fusedClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private var meMarker: Marker? = null
    private var routeLine: Polyline? = null
    private var lastLocation: Location? = null
    private var centeredOnce = false
    private val kaabaPoint = GeoPoint(QiblaCalculator.KAABA_LAT, QiblaCalculator.KAABA_LNG)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // osmdroid must be configured before the MapView is inflated
        val appCtx = requireContext().applicationContext
        val cfg = Configuration.getInstance()
        cfg.userAgentValue = appCtx.packageName
        val base = File(appCtx.cacheDir, "osmdroid")
        cfg.osmdroidBasePath = base
        cfg.osmdroidTileCache = File(base, "tiles")

        _binding = FragmentMapGpsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fusedClient = LocationServices.getFusedLocationProviderClient(requireContext())

        val map = binding.mapView
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(4.0)
        map.controller.setCenter(kaabaPoint)

        val kaaba = Marker(map)
        kaaba.position = kaabaPoint
        kaaba.title = "Kaaba, Makkah"
        kaaba.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        map.overlays.add(kaaba)

        binding.btnMyLocation.setOnClickListener {
            val loc = lastLocation
            if (loc != null) {
                binding.mapView.controller.setZoom(16.0)
                binding.mapView.controller.animateTo(GeoPoint(loc.latitude, loc.longitude))
            } else {
                Toast.makeText(requireContext(), "Waiting for GPS fix…", Toast.LENGTH_SHORT).show()
            }
        }
        binding.btnShowKaaba.setOnClickListener {
            binding.mapView.controller.setZoom(15.0)
            binding.mapView.controller.animateTo(kaabaPoint)
        }
        binding.btnCopyCoords.setOnClickListener { copyCoordinates() }
        binding.btnOpenMaps.setOnClickListener { openInMapsApp() }
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        startUpdates()
    }

    override fun onPause() {
        super.onPause()
        stopUpdates()
        binding.mapView.onPause()
    }

    @SuppressLint("MissingPermission")
    private fun startUpdates() {
        if (!LocationHelper.hasLocationPermission(requireContext())) {
            binding.tvGpsStatus.text = "Location permission needed — enable it in system Settings."
            return
        }
        binding.tvGpsStatus.text = "Searching GPS…"
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation
                if (loc != null) onLocation(loc)
            }
        }
        locationCallback = callback
        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        fusedClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null && lastLocation == null) onLocation(loc)
        }
    }

    private fun stopUpdates() {
        val cb = locationCallback
        if (cb != null) fusedClient.removeLocationUpdates(cb)
        locationCallback = null
    }

    private fun onLocation(loc: Location) {
        val b = _binding ?: return
        lastLocation = loc
        val here = GeoPoint(loc.latitude, loc.longitude)

        b.tvLat.text = String.format(Locale.US, "%.6f°", loc.latitude)
        b.tvLon.text = String.format(Locale.US, "%.6f°", loc.longitude)
        b.tvAlt.text = if (loc.hasAltitude()) String.format(Locale.US, "%.0f m", loc.altitude) else "n/a"
        b.tvAcc.text = if (loc.hasAccuracy()) String.format(Locale.US, "±%.0f m", loc.accuracy) else "n/a"
        b.tvSpeed.text = if (loc.hasSpeed()) String.format(Locale.US, "%.1f km/h", loc.speed * 3.6f) else "n/a"

        val result = FloatArray(1)
        Location.distanceBetween(
            loc.latitude, loc.longitude,
            QiblaCalculator.KAABA_LAT, QiblaCalculator.KAABA_LNG, result
        )
        b.tvKaabaDist.text = String.format(Locale.US, "%,.0f km", result[0] / 1000f)

        val time = DateFormat.getTimeInstance().format(Date(loc.time))
        b.tvGpsStatus.text = String.format(Locale.US, "GPS locked · updated %s", time)

        val map = b.mapView
        val marker = meMarker ?: createMeMarker(map).also {
            meMarker = it
            map.overlays.add(it)
        }
        marker.position = here

        val line = routeLine ?: createRouteLine().also {
            routeLine = it
            map.overlays.add(0, it)
        }
        line.setPoints(listOf(here, kaabaPoint))

        if (!centeredOnce) {
            centeredOnce = true
            map.controller.setZoom(13.0)
            map.controller.setCenter(here)
        }
        map.invalidate()
    }

    private fun createMeMarker(map: MapView): Marker {
        val m = Marker(map)
        m.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_location)
        m.title = "You are here"
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        return m
    }

    private fun createRouteLine(): Polyline {
        val line = Polyline()
        line.outlinePaint.color = Color.parseColor("#1B5E20")
        line.outlinePaint.strokeWidth = 6f
        line.outlinePaint.pathEffect = DashPathEffect(floatArrayOf(18f, 14f), 0f)
        line.setGeodesic(true)
        return line
    }

    private fun copyCoordinates() {
        val loc = lastLocation
        if (loc == null) {
            Toast.makeText(requireContext(), "No GPS fix yet", Toast.LENGTH_SHORT).show()
            return
        }
        val text = String.format(Locale.US, "%.6f, %.6f", loc.latitude, loc.longitude)
        val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("coordinates", text))
        Toast.makeText(requireContext(), "Copied: $text", Toast.LENGTH_SHORT).show()
    }

    private fun openInMapsApp() {
        val loc = lastLocation
        if (loc == null) {
            Toast.makeText(requireContext(), "No GPS fix yet", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = Uri.parse(String.format(Locale.US, "geo:%.6f,%.6f?q=%.6f,%.6f", loc.latitude, loc.longitude, loc.latitude, loc.longitude))
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(requireContext(), "No maps app found", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        stopUpdates()
        binding.mapView.onDetach()
        meMarker = null
        routeLine = null
        _binding = null
        super.onDestroyView()
    }
}
