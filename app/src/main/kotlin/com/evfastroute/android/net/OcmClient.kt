package com.evfastroute.android.net

import com.evfastroute.android.BuildConfig
import com.evfastroute.core.Charger
import com.evfastroute.core.OpenChargeMap
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

// Open Charge Map live charger fetch. Same API as iOS; no key → empty (routing then fails closed,
// exactly like iOS). Parsing/identity is done by the shared :core OpenChargeMap.

object OcmClient {

    private val cache = TimedMemoryCache<String, List<Charger>>(maxEntries = 48, ttlMillis = 5 * 60_000L)

    suspend fun chargers(
        minLat: Double,
        minLon: Double,
        maxLat: Double,
        maxLon: Double,
        maxResults: Int = 200,
    ): ServiceResult<List<Charger>> {
        val key = BuildConfig.OCM_API_KEY
        if (key.isBlank()) {
            return ServiceResult.Failure(ServiceFailure(ServiceFailureKind.CONFIGURATION))
        }

        val cacheKey = listOf(minLat, minLon, maxLat, maxLon)
            .joinToString(",") { String.format(java.util.Locale.US, "%.4f", it) } + ":$maxResults"
        cache.get(cacheKey)?.let { return ServiceResult.Success(it) }

        val boundingBox = "($maxLat,$minLon),($minLat,$maxLon)"
        val url = "${BuildConfig.OCM_BASE_URL.trimEnd('/')}/poi".toHttpUrl().newBuilder()
            .addQueryParameter("output", "json")
            .addQueryParameter("maxresults", maxResults.coerceIn(1, 500).toString())
            .addQueryParameter("boundingbox", boundingBox)
            .addQueryParameter("compact", "false")
            // OCM POIs carry provider-specific licenses. Restrict public-product results to
            // providers OCM marks as open-data licensed; broader imports require legal review.
            .addQueryParameter("opendata", "true")
            .build()

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
