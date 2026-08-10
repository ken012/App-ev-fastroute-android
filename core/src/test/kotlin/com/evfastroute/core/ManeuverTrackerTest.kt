package com.evfastroute.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ManeuverTrackerTest {
    private val steps = listOf(
        DrivingStep("Turn right", 100.0, LatLon(45.0000, -74.0000)),
        DrivingStep("Turn left", 200.0, LatLon(45.0010, -74.0000)),
    )

    @Test
    fun advancesInsideArrivalRadiusAndNeverMovesBackward() {
        val progress = ManeuverTracker.current(
            user = LatLon(45.0000, -74.0000),
            maneuvers = steps,
            lastReachedIndex = 0,
        )!!
        assertEquals(1, progress.index)
        assertEquals(1, ManeuverTracker.current(
            user = LatLon(45.0005, -74.0000),
            maneuvers = steps,
            lastReachedIndex = 1,
        )!!.index)
    }

    @Test
    fun distanceFormattingMatchesIos() {
        assertEquals("500 m", ManeuverTracker.formatDistance(503.0, usesMiles = false))
        assertEquals("0.3 mi", ManeuverTracker.formatDistance(503.0, usesMiles = true))
    }
}
