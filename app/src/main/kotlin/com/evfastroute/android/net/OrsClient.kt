package com.evfastroute.android.net

import com.evfastroute.android.BuildConfig
import com.evfastroute.core.OpenRouteService
import com.evfastroute.core.RouteLeg
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

// OpenRouteService driving-route fetch. Routing fails closed and preserves a typed reason for UI.

object OrsClient {

    private val jsonMedia = "application/json".toMediaType()
    private val cache = TimedMemoryCache<String, RouteLeg>(maxEntries = 256, ttlMillis = 15 * 60_000L)

    suspend fun route(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double,
    ): ServiceResult<RouteLeg> {
        val key = BuildConfig.ORS_API_KEY
        if (key.isBlank()) {
            return ServiceResult.Failure(ServiceFailure(ServiceFailureKind.CONFIGURATION))
        }

        val cacheKey = listOf(fromLat, fromLon, toLat, toLon)
            .joinToString(",") { String.format(java.util.Locale.US, "%.5f", it) }
        cache.get(cacheKey)?.let { return ServiceResult.Success(it) }

        val body = OpenRouteService.requestBody(fromLat, fromLon, toLat, toLon).toRequestBody(jsonMedia)
        val request = Request.Builder()
            .url("${BuildConfig.ORS_BASE_URL.trimEnd('/')}/v2/directions/driving-car/geojson")
            .addHeader("Authorization", key)
            .addHeader("Accept", "application/geo+json, application/json")
            .addHeader("User-Agent", "EVFastRoute-Android/${BuildConfig.VERSION_NAME}")
            .post(body)
            .build()

        return when (val response = fetchText(request)) {
            is ServiceResult.Failure -> response
            is ServiceResult.Success -> OpenRouteService.parse(response.value)?.let { leg ->
                cache.put(cacheKey, leg)
                ServiceResult.Success(leg)
            } ?: ServiceResult.Failure(ServiceFailure(ServiceFailureKind.INVALID_RESPONSE))
        }
    }
}
