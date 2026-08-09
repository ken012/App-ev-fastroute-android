package com.evfastroute.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

// OpenRouteService directions parsing → RouteLeg. Android's free replacement for MKDirections:
// the GeoJSON directions response gives the leg distance/duration + the road geometry the
// corridor projection and map need. Request the `.../geojson` endpoint so coordinates are
// explicit (no polyline decoding).

/** One driving leg: distance, duration, and the route corridor points. */
data class RouteLeg(
    val distanceKm: Double,
    val durationMinutes: Int,
    val geometry: List<LatLon>,
)

// --- GeoJSON response DTOs ---

@Serializable
private data class OrsSummary(val distance: Double? = null, val duration: Double? = null)

@Serializable
private data class OrsProperties(val summary: OrsSummary? = null)

@Serializable
private data class OrsGeometry(val coordinates: List<List<Double>> = emptyList())

@Serializable
private data class OrsFeature(val properties: OrsProperties? = null, val geometry: OrsGeometry? = null)

@Serializable
private data class OrsResponse(val features: List<OrsFeature> = emptyList())

object OpenRouteService {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Request body for the ORS directions POST: `{"coordinates":[[lon,lat],[lon,lat]]}`. */
    fun requestBody(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): String =
        """{"coordinates":[[$fromLon,$fromLat],[$toLon,$toLat]]}"""

    fun parse(geojsonBody: String): RouteLeg? = runCatching {
        val response = json.decodeFromString<OrsResponse>(geojsonBody)
        val feature = response.features.firstOrNull() ?: return null
        val summary = feature.properties?.summary ?: return null
        val distanceMeters = summary.distance ?: return null
        val durationSeconds = summary.duration ?: return null
        if (!distanceMeters.isFinite() || distanceMeters <= 0.0) return null
        if (!durationSeconds.isFinite() || durationSeconds <= 0.0) return null
        // GeoJSON coordinates are [longitude, latitude].
        val geometry = (feature.geometry?.coordinates ?: emptyList()).mapNotNull { coord ->
            if (coord.size < 2) return@mapNotNull null
            val lon = coord[0]
            val lat = coord[1]
            if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) null
            else LatLon(latitude = lat, longitude = lon)
        }
        if (geometry.size < 2) return null
        return RouteLeg(
            distanceKm = distanceMeters / 1000.0,
            durationMinutes = maxOf(1, (durationSeconds / 60.0).roundToInt()),
            geometry = geometry,
        )
    }.getOrNull()
}
