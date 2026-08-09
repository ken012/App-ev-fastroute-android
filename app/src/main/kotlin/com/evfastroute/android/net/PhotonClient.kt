package com.evfastroute.android.net

import com.evfastroute.android.BuildConfig
import com.evfastroute.core.PlaceCandidate
import com.evfastroute.core.Photon
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

// Photon (OSM) address search — Android's free MKLocalSearch replacement. Keyless. Results feed
// the shared :core PlaceRanker for iOS-identical relevance ordering.

object PhotonClient {

    private val cache = TimedMemoryCache<String, List<PlaceCandidate>>(maxEntries = 96, ttlMillis = 5 * 60_000L)

    suspend fun search(
        query: String,
        anchorLat: Double? = null,
        anchorLon: Double? = null,
        limit: Int = 15,
    ): ServiceResult<List<PlaceCandidate>> {
        if (query.isBlank()) return ServiceResult.Success(emptyList())

        val anchorKey = if (anchorLat != null && anchorLon != null) {
            String.format(java.util.Locale.US, "%.3f,%.3f", anchorLat, anchorLon)
        } else {
            "global"
        }
        val cacheKey = "${query.trim().lowercase()}|$anchorKey|$limit"
        cache.get(cacheKey)?.let { return ServiceResult.Success(it) }

        val builder = "${BuildConfig.PHOTON_BASE_URL.trimEnd('/')}/api/".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("limit", limit.coerceIn(1, 50).toString())
        if (anchorLat != null && anchorLon != null) {
            builder.addQueryParameter("lat", anchorLat.toString())
            builder.addQueryParameter("lon", anchorLon.toString())
        }

        val request = Request.Builder()
            .url(builder.build())
            .addHeader("Accept", "application/geo+json, application/json")
            .addHeader("User-Agent", "EVFastRoute-Android/${BuildConfig.VERSION_NAME}")
            .build()
        return when (val response = fetchText(request)) {
            is ServiceResult.Failure -> response
            is ServiceResult.Success -> {
                val places = Photon.parseOrNull(response.value)
                    ?: return ServiceResult.Failure(ServiceFailure(ServiceFailureKind.INVALID_RESPONSE))
                cache.put(cacheKey, places)
                ServiceResult.Success(places)
            }
        }
    }
}
