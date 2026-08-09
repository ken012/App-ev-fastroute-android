package com.evfastroute.android.nav

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle

// Thin wrapper over the framework LocationManager (no Google Play Services → stays free/keyless).
// Feeds coarse position samples to the guided-trip arrival detector. The caller must hold the
// ACCESS_FINE_LOCATION permission; start() no-ops safely if it doesn't.
class LocationProvider(private val context: Context) {

    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var listener: LocationListener? = null

    fun start(onSample: (latitude: Double, longitude: Double, accuracyMeters: Double?, sampleMillis: Long) -> Unit) {
        stop()
        if (!hasPermission()) return
        val newListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val accuracy = if (location.hasAccuracy()) location.accuracy.toDouble() else null
                onSample(location.latitude, location.longitude, accuracy, location.time)
            }
            // Kept as explicit no-ops so this works on API 26–29 where they aren't default methods.
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        listener = newListener
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        runCatching {
            providers.forEach { provider ->
                manager.requestLocationUpdates(provider, MIN_INTERVAL_MILLIS, MIN_DISTANCE_METERS, newListener)
            }
        }
    }

    fun stop() {
        listener?.let { l -> runCatching { manager.removeUpdates(l) } }
        listener = null
    }

    private fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val MIN_INTERVAL_MILLIS = 5_000L
        const val MIN_DISTANCE_METERS = 10f
    }
}
