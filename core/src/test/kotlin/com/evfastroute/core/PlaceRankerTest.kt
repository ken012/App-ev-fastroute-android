package com.evfastroute.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Ports the iOS SearchRankingTests so search relevance behaves identically across platforms. */
class PlaceRankerTest {

    private fun place(
        name: String, address: String, km: Double?, lat: Double = 45.0, lon: Double = -74.0,
    ) = PlaceCandidate(placeName = name, fullAddress = address, latitude = lat, longitude = lon, distanceKm = km)

    @Test
    fun relevanceBeatsProximityForNamedCity() {
        val td = place("TD Canada Trust", "100 Main St, Ottawa, Canada", 2.0, 45.0, -75.0)
        val city = place("Toronto", "Toronto, ON, Canada", 400.0, 43.6, -79.3)
        val ranked = PlaceRanker.rank(listOf(td, city), query = "Toronto", haveAnchor = true)
        assertEquals("Toronto", ranked.first().placeName)
    }

    @Test
    fun rankingIsAccentInsensitive() {
        val unrelated = place("Quebec Avenue", "Ottawa, ON", 3.0, 45.0, -75.0)
        val city = place("Québec", "Québec, QC, Canada", 400.0, 46.8, -71.2)
        val ranked = PlaceRanker.rank(listOf(unrelated, city), query = "Quebec", haveAnchor = true)
        assertEquals("Québec", ranked.first().placeName)
    }

    @Test
    fun smallTypoDoesNotDestroyTheProviderMatch() {
        val unrelated = place("Wallace Market", "Ottawa, ON", 1.0, 45.0, -75.0)
        val walmart = place("Walmart Supercentre", "Hawkesbury, ON", 12.0, 45.6, -74.6)
        val ranked = PlaceRanker.rank(listOf(unrelated, walmart), query = "Walmrt", haveAnchor = true)
        assertEquals("Walmart Supercentre", ranked.first().placeName)
    }

    @Test
    fun duplicateProviderRecordsAreCollapsed() {
        val a = place("Walmart Supercentre", "Hawkesbury", 5.0, 45.60001, -74.60001)
        val b = place("Walmart Supercentre", "County Road 17, Hawkesbury", 5.01, 45.60002, -74.60002)
        assertEquals(1, PlaceRanker.deduplicated(listOf(a, b)).size)
    }

    @Test
    fun weakOrSparseResultsTriggerBroaderSearch() {
        val weak = place("TD Canada Trust", "Ottawa, ON", 2.0)
        assertTrue(PlaceRanker.shouldBroaden(listOf(weak), "Toronto"))
        val strong = (0 until 6).map { index ->
            place("Walmart Supercentre", "Store $index", (index + 1).toDouble(), 45.0 + index * 0.01, -74.0)
        }
        assertFalse(PlaceRanker.shouldBroaden(strong, "Walmart"))
    }
}
