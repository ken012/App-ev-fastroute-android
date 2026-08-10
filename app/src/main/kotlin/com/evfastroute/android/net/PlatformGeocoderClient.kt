package com.evfastroute.android.net

import android.content.Context
import android.location.Address
import android.location.Geocoder
import com.evfastroute.core.LatLon
import com.evfastroute.core.PlaceCandidate
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Uses the geocoder installed by the device manufacturer as a second search source. On many
 * consumer phones this materially improves nearby business/address lookup; devices without one
 * continue on Photon. The legacy blocking API is deliberately isolated on Dispatchers.IO because
 * it remains the only implementation that works consistently across this app's API 26–36 range. */
class PlatformGeocoderClient(context: Context) {
    private val appContext = context.applicationContext
    private val cache = TimedMemoryCache<String, List<PlaceCandidate>>(maxEntries = 64, ttlMillis = 5 * 60_000L)

    suspend fun search(
        query: String,
        anchor: LatLon?,
        limit: Int = 15,
    ): ServiceResult<List<PlaceCandidate>> = withContext(Dispatchers.IO) {
        if (query.isBlank() || !Geocoder.isPresent()) return@withContext ServiceResult.Success(emptyList())
        val safeLimit = limit.coerceIn(1, 25)
        val anchorKey = anchor?.let {
            String.format(Locale.US, "%.3f,%.3f", it.latitude, it.longitude)
        } ?: "global"
        val cacheKey = "${query.trim().lowercase(Locale.ROOT)}|$anchorKey|$safeLimit"
        cache.get(cacheKey)?.let { return@withContext ServiceResult.Success(it) }

        try {
            @Suppress("DEPRECATION")
            val addresses = Geocoder(appContext, Locale.getDefault()).let { geocoder ->
                if (anchor == null) {
                    geocoder.getFromLocationName(query, safeLimit)
                } else {
                    val latitudeRadius = 3.0
                    val longitudeRadius = (3.0 / kotlin.math.cos(Math.toRadians(anchor.latitude)))
                        .coerceIn(3.0, 12.0)
                    geocoder.getFromLocationName(
                        query,
                        safeLimit,
                        (anchor.latitude - latitudeRadius).coerceAtLeast(-90.0),
                        (anchor.longitude - longitudeRadius).coerceAtLeast(-180.0),
                        (anchor.latitude + latitudeRadius).coerceAtMost(90.0),
                        (anchor.longitude + longitudeRadius).coerceAtMost(180.0),
                    )
                }
            }.orEmpty()
            val places = addresses.mapNotNull(::toCandidate)
            cache.put(cacheKey, places)
            ServiceResult.Success(places)
        } catch (_: IOException) {
            ServiceResult.Failure(ServiceFailure(ServiceFailureKind.NETWORK))
        } catch (_: IllegalArgumentException) {
            ServiceResult.Failure(ServiceFailure(ServiceFailureKind.INVALID_RESPONSE))
        } catch (_: RuntimeException) {
            ServiceResult.Failure(ServiceFailure(ServiceFailureKind.INVALID_RESPONSE))
        }
    }

    private fun toCandidate(address: Address): PlaceCandidate? {
        if (!address.hasLatitude() || !address.hasLongitude()) return null
        val latitude = address.latitude
        val longitude = address.longitude
        if (!latitude.isFinite() || !longitude.isFinite() || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
            return null
        }
        val street = listOfNotNull(address.subThoroughfare, address.thoroughfare)
            .joinToString(" ")
            .trim()
        val feature = address.featureName?.trim().orEmpty()
        val name = when {
            feature.isNotBlank() && feature != address.subThoroughfare -> feature
            street.isNotBlank() -> street
            !address.locality.isNullOrBlank() -> address.locality
            !address.adminArea.isNullOrBlank() -> address.adminArea
            else -> "Unknown place"
        }
        val formatted = runCatching { address.getAddressLine(0) }.getOrNull()?.trim().orEmpty()
        val fallbackAddress = listOfNotNull(
            street.takeIf(String::isNotBlank),
            address.locality,
            address.adminArea,
            address.postalCode,
            address.countryName,
        ).filter { it.isNotBlank() }.distinct().joinToString(", ")
        return PlaceCandidate(
            placeName = name,
            fullAddress = formatted.ifBlank { fallbackAddress },
            latitude = latitude,
            longitude = longitude,
            countryCode = address.countryCode?.trim()?.uppercase()?.takeIf { it.length == 2 },
        )
    }
}
