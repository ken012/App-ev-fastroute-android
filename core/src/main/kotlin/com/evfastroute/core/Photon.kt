package com.evfastroute.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Photon (Komoot, OSM-based) geocoding parsing → PlaceCandidate. Android's free replacement for
// MKLocalSearch. Output feeds the shared PlaceRanker, so search relevance is ranked identically
// to iOS regardless of the provider.

@Serializable
private data class PhotonGeometry(val coordinates: List<Double> = emptyList())

@Serializable
private data class PhotonProperties(
    val name: String? = null,
    val street: String? = null,
    val housenumber: String? = null,
    val city: String? = null,
    val state: String? = null,
    val postcode: String? = null,
    val country: String? = null,
    val countrycode: String? = null,
)

@Serializable
private data class PhotonFeature(val geometry: PhotonGeometry? = null, val properties: PhotonProperties? = null)

@Serializable
private data class PhotonResponse(val features: List<PhotonFeature> = emptyList())

object Photon {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(geojsonBody: String): List<PlaceCandidate> =
        json.decodeFromString<PhotonResponse>(geojsonBody).features.mapNotNull { feature ->
            val coords = feature.geometry?.coordinates ?: return@mapNotNull null
            if (coords.size < 2) return@mapNotNull null
            val props = feature.properties ?: PhotonProperties()

            val streetLine = listOfNotNull(props.housenumber, props.street)
                .joinToString(" ")
                .ifBlank { null }
            val placeName = props.name ?: streetLine ?: props.city ?: "Unknown place"
            val fullAddress = listOfNotNull(streetLine, props.city, props.state, props.postcode, props.country)
                .joinToString(", ")

            // GeoJSON is [lon, lat].
            PlaceCandidate(
                placeName = placeName,
                fullAddress = fullAddress,
                latitude = coords[1],
                longitude = coords[0],
            )
        }
}
