package com.evfastroute.android.net

import com.evfastroute.android.BuildConfig
import com.evfastroute.core.Charger
import com.evfastroute.core.OpenChargeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

// Open Charge Map live charger fetch. Same API as iOS; no key → empty (routing then fails closed,
// exactly like iOS). Parsing/identity is done by the shared :core OpenChargeMap.

object OcmClient {

    suspend fun chargers(
        minLat: Double,
        minLon: Double,
        maxLat: Double,
        maxLon: Double,
        maxResults: Int = 200,
    ): List<Charger> = withContext(Dispatchers.IO) {
        val key = BuildConfig.OCM_API_KEY
        if (key.isBlank()) return@withContext emptyList()

        val boundingBox = "($maxLat,$minLon),($minLat,$maxLon)"
        val url = "https://api.openchargemap.io/v3/poi".toHttpUrl().newBuilder()
            .addQueryParameter("output", "json")
            .addQueryParameter("maxresults", maxResults.toString())
            .addQueryParameter("boundingbox", boundingBox)
            .addQueryParameter("key", key)
            .build()

        runCatching {
            httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList<Charger>()
                val body = response.body?.string() ?: return@use emptyList<Charger>()
                OpenChargeMap.parse(body)
            }
        }.getOrDefault(emptyList())
    }
}
