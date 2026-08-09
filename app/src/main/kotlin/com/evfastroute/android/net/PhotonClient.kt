package com.evfastroute.android.net

import com.evfastroute.core.PlaceCandidate
import com.evfastroute.core.Photon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

// Photon (OSM) address search — Android's free MKLocalSearch replacement. Keyless. Results feed
// the shared :core PlaceRanker for iOS-identical relevance ordering.

object PhotonClient {

    suspend fun search(
        query: String,
        anchorLat: Double? = null,
        anchorLon: Double? = null,
        limit: Int = 15,
    ): List<PlaceCandidate> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val builder = "https://photon.komoot.io/api/".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("limit", limit.toString())
        if (anchorLat != null && anchorLon != null) {
            builder.addQueryParameter("lat", anchorLat.toString())
            builder.addQueryParameter("lon", anchorLon.toString())
        }

        runCatching {
            httpClient.newCall(Request.Builder().url(builder.build()).build()).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList<PlaceCandidate>()
                val body = response.body?.string() ?: return@use emptyList<PlaceCandidate>()
                Photon.parse(body)
            }
        }.getOrDefault(emptyList())
    }
}
