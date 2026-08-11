package com.evfastroute.android.net

import com.evfastroute.android.BuildConfig
import com.evfastroute.core.Charger
import com.evfastroute.core.OpenChargeMap
import okhttp3.HttpUrl
import okhttp3.Request

// Open Charge Map live charger fetch. Same API as iOS; no key → empty (routing then fails closed,
// exactly like iOS). Parsing/identity is done by the shared :core OpenChargeMap.

object OcmClient {

    private val cache = TimedMemoryCache<String, List<Charger>>(maxEntries = 48, ttlMillis = 5 * 60_000L)
    val isConfigured: Boolean get() = BuildConfig.OCM_API_KEY.isNotBlank()

    suspend fun chargers(
        minLat: Double,
        minLon: Double,
        maxLat: Double,
        maxLon: Double,
        maxResults: Int = 200,
        minPowerKw: Int = 25,
    ): ServiceResult<List<Charger>> {
        val key = BuildConfig.OCM_API_KEY
        if (key.isBlank()) {
            return ServiceResult.Failure(ServiceFailure(ServiceFailureKind.CONFIGURATION))
        }
        val bounds = OcmBounds(minLat, minLon, maxLat, maxLon)
        val cacheKey = ocmCacheKey(bounds, maxResults, minPowerKw)
        cache.get(cacheKey)?.let { return ServiceResult.Success(it) }

        val url = ocmPoiUrl(BuildConfig.OCM_BASE_URL, bounds, maxResults, minPowerKw)
            ?: return ServiceResult.Failure(ServiceFailure(ServiceFailureKind.CONFIGURATION))

        val request = Request.Builder()
            .url(url)
            .addHeader("X-API-Key", key)
            .addHeader("Accept", "application/json")
            .addHeader("User-Agent", "EVFastRoute-Android/${BuildConfig.VERSION_NAME}")
            .build()
        return when (val response = fetchText(request)) {
            is ServiceResult.Failure -> response
            is ServiceResult.Success -> {
                val chargers = OpenChargeMap.parseOrNull(response.value)
                    ?: return ServiceResult.Failure(ServiceFailure(ServiceFailureKind.INVALID_RESPONSE))
                cache.put(cacheKey, chargers)
                ServiceResult.Success(chargers)
            }
        }
    }
}

data class OcmBounds(
    val minLat: Double,
    val minLon: Double,
    val maxLat: Double,
    val maxLon: Double,
)

internal fun ocmCacheKey(bounds: OcmBounds, maxResults: Int, minPowerKw: Int): String =
    listOf(bounds.minLat, bounds.minLon, bounds.maxLat, bounds.maxLon)
        .joinToString(",") { String.format(java.util.Locale.US, "%.4f", it) } +
        ":${maxResults.coerceIn(1, 500)}:${minPowerKw.coerceAtLeast(0)}"

internal fun ocmPoiUrl(
    baseUrl: String,
    bounds: OcmBounds,
    maxResults: Int,
    minPowerKw: Int,
): HttpUrl? {
    val endpoint = configuredHttpsUrl(baseUrl, "poi") ?: return null
    val boundingBox = "(${bounds.maxLat},${bounds.minLon}),(${bounds.minLat},${bounds.maxLon})"
    return endpoint.newBuilder()
        .addQueryParameter("output", "json")
        .addQueryParameter("maxresults", maxResults.coerceIn(1, 500).toString())
        .addQueryParameter("boundingbox", boundingBox)
        .apply {
            if (minPowerKw > 0) addQueryParameter("minpowerkw", minPowerKw.toString())
        }
        .build()
}
