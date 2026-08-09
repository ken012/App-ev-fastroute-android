package com.evfastroute.core

import kotlin.test.Test
import kotlin.test.assertEquals

/** Ports the iOS RouteObjective behavior so titles/modes and back-compat match across platforms. */
class RouteObjectiveTest {

    @Test
    fun plannerCasesAreTheFourUserObjectivesInOrder() {
        assertEquals(
            listOf(
                RouteObjective.FASTEST,
                RouteObjective.RELIABLE,
                RouteObjective.LOWEST_COST,
                RouteObjective.FEWEST_STOPS,
            ),
            RouteObjective.plannerCases,
        )
    }

    @Test
    fun titleAndModeMatchIOS() {
        assertEquals("Fewest charging stops", RouteObjective.FEWEST_STOPS.title)
        assertEquals("Fewest stops", RouteObjective.FEWEST_STOPS.mode)
        assertEquals("Fastest", RouteObjective.FASTEST.mode)
    }

    @Test
    fun legacyModeStringMapsBack() {
        assertEquals(RouteObjective.FEWEST_STOPS, RouteObjective.fromLegacyMode("Fewest stops"))
        assertEquals(RouteObjective.LOWEST_COST, RouteObjective.fromLegacyMode("Lowest estimate"))
        assertEquals(RouteObjective.FASTEST, RouteObjective.fromLegacyMode("fastest"))
        assertEquals(RouteObjective.VERIFIED, RouteObjective.fromLegacyMode("something unknown"))
    }

    @Test
    fun serializedRoundTripsAndMatchesSwiftRawValues() {
        for (objective in RouteObjective.entries) {
            assertEquals(objective, RouteObjective.fromSerialized(objective.serialized))
        }
        assertEquals("lowestCost", RouteObjective.LOWEST_COST.serialized)
        assertEquals("fewestStops", RouteObjective.FEWEST_STOPS.serialized)
    }
}
