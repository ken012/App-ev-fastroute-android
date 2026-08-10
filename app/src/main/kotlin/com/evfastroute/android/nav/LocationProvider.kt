package com.evfastroute.android.nav

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper

// Thin wrapper over the framework LocationManager (no Google Play Services → stays free/keyless).
// Feeds coarse position samples to the guided-trip arrival detector. The caller must hold the
// either foreground location permission; start() no-ops safely if neither is granted.
class LocationProvider(private val context: Context) {

    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var listener: LocationListener? = null
    private var timeout: Runnable? = null

    // Both entry points perform a runtime coarse-or-fine permission check immediately before
    // touching LocationManager and also catch revocation races around every provider call.
    @SuppressLint("MissingPermission")
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
        providers.forEach { provider ->
            runCatching { manager.requestLocationUpdates(provider, MIN_INTERVAL_MILLIS, MIN_DISTANCE_METERS, newListener) }
        }
    }

    fun stop() {
        timeout?.let(mainHandler::removeCallbacks)
        timeout = null
        listener?.let { l -> runCatching { manager.removeUpdates(l) } }
        listener = null
    }

    /** Best recent device fix for "Use current location"; otherwise waits for one fresh sample. */
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION") // Required fallback for the app's supported API 26–29 devices.
    fun oneShot(
        onSample: (latitude: Double, longitude: Double, accuracyMeters: Double?, sampleMillis: Long) -> Unit,
        onUnavailable: () -> Unit,
    ) {
        stop()
        if (!hasPermission()) {
            onUnavailable()
            return
        }
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        val recent = providers.mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.filter { it.time > System.currentTimeMillis() - LAST_LOCATION_MAX_AGE_MILLIS }
            .minByOrNull { if (it.hasAccuracy()) it.accuracy else Float.MAX_VALUE }
        if (recent != null) {
            onSample(recent.latitude, recent.longitude, recent.accuracyOrNull(), recent.time)
            return
        }
        if (providers.isEmpty()) {
            onUnavailable()
            return
        }
        var completed = false
        fun complete(location: Location?) {
            if (completed) return
            completed = true
            stop()
            if (location == null) {
                onUnavailable()
            } else {
                onSample(location.latitude, location.longitude, location.accuracyOrNull(), location.time)
            }
        }
        val newListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                complete(location)
            }
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        listener = newListener
        var requested = false
        providers.forEach { provider ->
            val accepted = runCatching {
                manager.requestSingleUpdate(provider, newListener, null)
                true
            }.getOrDefault(false)
            requested = requested || accepted
        }
        if (requested) {
            Runnable { complete(null) }.also { task ->
                timeout = task
                mainHandler.postDelayed(task, ONE_SHOT_TIMEOUT_MILLIS)
            }
        } else {
            complete(null)
        }
    }

    private fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun Location.accuracyOrNull(): Double? =
        if (hasAccuracy()) accuracy.toDouble() else null

    private companion object {
        const val MIN_INTERVAL_MILLIS = 5_000L
        const val MIN_DISTANCE_METERS = 10f
        const val LAST_LOCATION_MAX_AGE_MILLIS = 10 * 60 * 1_000L
        const val ONE_SHOT_TIMEOUT_MILLIS = 15_000L
    }
}
