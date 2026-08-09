package com.evfastroute.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OpenRouteServiceTest {

    private val geojson = """
        {
          "type": "FeatureCollection",
          "features": [
            {
              "type": "Feature",
              "properties": { "summary": { "distance": 12345.6, "duration": 780.0 } },
              "geometry": { "type": "LineString", "coordinates": [ [-74.0, 45.0], [-75.0, 44.5] ] }
            }
          ]
        }
    """.trimIndent()

    @Test
    fun parsesLegDistanceDurationAndGeometry() {
        val leg = OpenRouteService.parse(geojson)!!
        assertEquals(12.3456, leg.distanceKm, 1e-9)
        assertEquals(13, leg.durationMinutes) // 780 s / 60 = 13 min
        assertEquals(2, leg.geometry.size)
        // GeoJSON is [lon, lat] → must be swapped into LatLon(lat, lon).
        assertEquals(45.0, leg.geometry[0].latitude, 1e-9)
        assertEquals(-74.0, leg.geometry[0].longitude, 1e-9)
    }

    @Test
    fun requestBodyIsLonLatOrdered() {
        assertEquals(
            """{"coordinates":[[-74.0,45.0],[-75.0,44.5]]}""",
            OpenRouteService.requestBody(fromLat = 45.0, fromLon = -74.0, toLat = 44.5, toLon = -75.0),
        )
    }

    @Test
    fun emptyFeaturesReturnsNull() {
        assertNull(OpenRouteService.parse("""{"type":"FeatureCollection","features":[]}"""))
    }

    @Test
    fun rejectsZeroSummaryShortGeometryInvalidCoordinatesAndMalformedJson() {
        assertNull(OpenRouteService.parse("""{"features":[{"properties":{"summary":{"distance":0,"duration":60}},"geometry":{"coordinates":[[0,0],[1,1]]}}]}"""))
        assertNull(OpenRouteService.parse("""{"features":[{"properties":{"summary":{"distance":100,"duration":60}},"geometry":{"coordinates":[[0,0]]}}]}"""))
        assertNull(OpenRouteService.parse("""{"features":[{"properties":{"summary":{"distance":100,"duration":60}},"geometry":{"coordinates":[[0,95],[1,1]]}}]}"""))
        assertNull(OpenRouteService.parse("not-json"))
    }
}
