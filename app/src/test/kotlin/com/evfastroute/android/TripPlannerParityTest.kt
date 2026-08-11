package com.evfastroute.android

import com.evfastroute.core.PlaceCandidate
import com.evfastroute.core.RouteObjective
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripPlannerParityTest {
    @Test
    fun candidateGenerationOrderMatchesIosDeduplicationContract() {
        assertEquals(
            listOf(
                RouteObjective.FEWEST_STOPS,
                RouteObjective.FASTEST,
                RouteObjective.RELIABLE,
                RouteObjective.LOWEST_COST,
            ),
            TripPlanner().candidateGenerationObjectives,
        )
    }

    @Test
    fun itineraryValidationMatchesIosCountryAndConsecutiveStopRules() {
        fun place(name: String, lat: Double, country: String?) = PlaceCandidate(
            placeName = name,
            fullAddress = name,
            latitude = lat,
            longitude = -74.0,
            countryCode = country,
        )

        val canada = place("Hawkesbury", 45.61, "CA")
        val nearbyDuplicate = place("Same stop", 45.6101, "CA")
        assertTrue(itineraryValidationError(listOf(canada, nearbyDuplicate))!!.contains("consecutive"))

        val australia = place("Sydney", -33.87, "AU")
        assertTrue(itineraryValidationError(listOf(canada, australia))!!.contains("AU"))

        val returnTrip = place("Return near Hawkesbury", 45.6103, "CA")
        val ottawa = place("Ottawa", 45.42, "CA")
        assertNull(itineraryValidationError(listOf(canada, ottawa, returnTrip)))

        assertTrue(itineraryValidationError(listOf(canada, ottawa, canada))!!.contains("destination"))
    }

    @Test
    fun everySuccessfulReplanProducesANewResultsNavigationSignal() {
        val firstPlan = nextSuccessfulPlanRevision(0L)
        val cachedReplan = nextSuccessfulPlanRevision(firstPlan)

        assertEquals(1L, firstPlan)
        assertEquals(2L, cachedReplan)
        assertTrue(shouldOpenRouteResults(cachedReplan, optionCount = 1))
    }

    @Test
    fun resultsNavigationRequiresASuccessfulNonEmptyPlan() {
        assertTrue(!shouldOpenRouteResults(successfulPlanRevision = 0L, optionCount = 1))
        assertTrue(!shouldOpenRouteResults(successfulPlanRevision = 1L, optionCount = 0))
        assertEquals(1L, nextSuccessfulPlanRevision(Long.MAX_VALUE))
    }
}
