package com.evfastroute.android.net

import com.evfastroute.android.BuildConfig
import com.evfastroute.core.OpenRouteService
import com.evfastroute.core.RouteLeg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

// OpenRouteService driving-route fetch (Android's free MKDirections replacement). No key → null,
// so routing fails closed. Parsing is done by the shared :core OpenRouteService.

object OrsClient {

    private val jsonMedia = "application/json".toMediaType()

    suspend fun route(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): RouteLeg? =
        withContext(Dispatchers.IO) {
            val key = BuildConfig.ORS_API_KEY
            if (key.isBlank()) return@withContext null

            val body = OpenRouteService.requestBody(fromLat, fromLon, toLat, toLon).toRequestBody(jsonMedia)
            val request = Request.Builder()
                .url("https://api.openrouteservice.org/v2/directions/driving-car/geojson")
                .addHeader("Authorization", key)
                .post(body)
                .build()

            runCatching {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val text = response.body?.string() ?: return@use null
                    OpenRouteService.parse(text)
                }
            }.getOrNull()
        }
}
