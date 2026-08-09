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
private data class OrsSummary(val distance: Double = 0.0, val duration: Double = 0.0)

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

    fun parse(geojsonBody: String): RouteLeg? {
        val response = json.decodeFromString<OrsResponse>(geojsonBody)
        val feature = response.features.firstOrNull() ?: return null
        val summary = feature.properties?.summary ?: return null
        // GeoJSON coordinates are [longitude, latitude].
        val geometry = (feature.geometry?.coordinates ?: emptyList()).mapNotNull { coord ->
            if (coord.size >= 2) LatLon(latitude = coord[1], longitude = coord[0]) else null
        }
        return RouteLeg(
            distanceKm = summary.distance / 1000.0,
            durationMinutes = maxOf(1, (summary.duration / 60.0).roundToInt()),
            geometry = geometry,
        )
    }
}
