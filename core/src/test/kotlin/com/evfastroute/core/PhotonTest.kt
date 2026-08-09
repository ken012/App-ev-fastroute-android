package com.evfastroute.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhotonTest {

    private val geojson = """
        {
          "type": "FeatureCollection",
          "features": [
            {
              "geometry": { "type": "Point", "coordinates": [-79.38, 43.65] },
              "properties": { "name": "Toronto", "state": "Ontario", "country": "Canada", "countrycode": "CA" }
            },
            {
              "geometry": { "type": "Point", "coordinates": [-75.7, 45.4] },
              "properties": {
                "housenumber": "123", "street": "Main Street", "city": "Ottawa",
                "state": "Ontario", "postcode": "K1A0A1", "country": "Canada"
              }
            }
          ]
        }
    """.trimIndent()

    @Test
    fun parsesCityAndAddressFeatures() {
        val places = Photon.parse(geojson)
        assertEquals(2, places.size)
        assertEquals("Toronto", places[0].placeName)
        assertEquals(43.65, places[0].latitude, 1e-9)  // [lon,lat] swapped correctly
        assertEquals(-79.38, places[0].longitude, 1e-9)
        assertEquals("123 Main Street", places[1].placeName)
        assertTrue(places[1].fullAddress.contains("Ottawa"))
    }

    @Test
    fun photonResultsRankThroughTheSharedRanker() {
        // End-to-end: Photon output → PlaceRanker picks the city for a city query.
        val ranked = PlaceRanker.rank(Photon.parse(geojson), query = "Toronto", haveAnchor = false)
        assertEquals("Toronto", ranked.first().placeName)
    }

    @Test
    fun emptyFeaturesReturnEmptyList() {
        assertTrue(Photon.parse("""{"type":"FeatureCollection","features":[]}""").isEmpty())
    }

    @Test
    fun invalidCoordinatesAndMalformedJsonReturnNoCandidates() {
        assertTrue(Photon.parse("""{"features":[{"geometry":{"coordinates":[0,100]},"properties":{"name":"Bad"}}]}""").isEmpty())
        assertTrue(Photon.parse("not-json").isEmpty())
        assertNull(Photon.parseOrNull("not-json"))
        assertEquals(emptyList(), Photon.parseOrNull("""{"type":"FeatureCollection","features":[]}"""))
    }
}
