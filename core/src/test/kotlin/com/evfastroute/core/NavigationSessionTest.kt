package com.evfastroute.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Locks the sequential-handoff session state machine (progress + conservative arrival prompt). */
class NavigationSessionTest {

    private val stopA = NavigationPoint(45.0, -75.0, "Charger A", NavigationPoint.Kind.CHARGING)
    private val destination = NavigationPoint(46.0, -76.0, "Home", NavigationPoint.Kind.DESTINATION)

    private fun session() = NavigationSession.create(
        stops = listOf(stopA), destination = destination, app = NavigationApp.WAZE, startedAtMillis = 1_000L,
    )

    @Test
    fun buildsItineraryAndInitialProgress() {
        val s = session()
        assertEquals(listOf(stopA, destination), s.itinerary)
        assertEquals(stopA, s.currentPoint)
        assertEquals(2, s.totalPointCount)
        assertEquals(2, s.remainingPointCount)
        assertEquals(0, s.completedPointCount)
        assertFalse(s.isComplete)
    }

    @Test
    fun advancingIsManualAndReachesCompletion() {
        val afterFirst = session().recordHandoff(10_000L).markCurrentPointComplete()
        assertEquals(destination, afterFirst.currentPoint)
        assertEquals(1, afterFirst.completedPointCount)
        assertNull(afterFirst.lastHandoffAtMillis) // handoff/prompt reset on advance
        assertNull(afterFirst.arrivalPromptedIndex)
        assertFalse(afterFirst.isComplete)

        val done = afterFirst.markCurrentPointComplete()
        assertTrue(done.isComplete)
        assertEquals(0, done.remainingPointCount)
        assertNull(done.currentPoint)
        // No-op once complete.
        assertEquals(done, done.markCurrentPointComplete())
    }

    @Test
    fun suggestsArrivalOnlyWhenFreshAccurateCloseAndSettled() {
        val s = session().recordHandoff(10_000L)
        // At the point, good accuracy, 30s after handoff → offer the prompt.
        assertTrue(s.shouldSuggestArrival(45.0, -75.0, horizontalAccuracyMeters = 20.0, sampleAtMillis = 40_000L, nowMillis = 40_000L))
    }

    @Test
    fun rejectsSuspectSamples() {
        val s = session().recordHandoff(10_000L)
        // No handoff recorded yet.
        assertFalse(session().shouldSuggestArrival(45.0, -75.0, 20.0, 40_000L, 40_000L))
        // Too soon after handoff (<20s).
        assertFalse(s.shouldSuggestArrival(45.0, -75.0, 20.0, 25_000L, 25_000L))
        // Sample older than the handoff.
        assertFalse(s.shouldSuggestArrival(45.0, -75.0, 20.0, 5_000L, 40_000L))
        // Stale or implausibly future samples.
        assertFalse(s.shouldSuggestArrival(45.0, -75.0, 20.0, 40_000L, 71_000L))
        assertFalse(s.shouldSuggestArrival(45.0, -75.0, 20.0, 46_000L, 40_000L))
        // Poor / missing / negative accuracy.
        assertFalse(s.shouldSuggestArrival(45.0, -75.0, 150.0, 40_000L, 40_000L))
        assertFalse(s.shouldSuggestArrival(45.0, -75.0, null, 40_000L, 40_000L))
        assertFalse(s.shouldSuggestArrival(45.0, -75.0, -1.0, 40_000L, 40_000L))
        // Too far from the point (~1.1 km north).
        assertFalse(s.shouldSuggestArrival(45.01, -75.0, 20.0, 40_000L, 40_000L))
        // Already prompted for this point.
        assertFalse(s.recordArrivalPrompt().shouldSuggestArrival(45.0, -75.0, 20.0, 40_000L, 40_000L))
    }

    @Test
    fun onlyRecentSessionsAreRestored() {
        val recent = session().copy(startedAtMillis = 1_000_000L)
        assertTrue(NavigationSession.isRestorable(recent, 1_000_000L + 60_000L))
        assertFalse(NavigationSession.isRestorable(recent, 1_000_000L + NavigationSession.MAX_SESSION_AGE_MILLIS + 1))
        assertFalse(NavigationSession.isRestorable(recent.copy(startedAtMillis = 2_000_000L), 1_000_000L))
    }

    @Test
    fun rejectsCorruptPersistedSessions() {
        val now = 1_100_000L
        val valid = session().copy(startedAtMillis = 1_000_000L)
        assertFalse(NavigationSession.isRestorable(valid.copy(nextIndex = -1), now))
        assertFalse(
            NavigationSession.isRestorable(
                valid.copy(itinerary = listOf(stopA.copy(latitude = Double.NaN)) + destination),
                now,
            ),
        )
        assertFalse(
            NavigationSession.isRestorable(
                valid.copy(itinerary = List(NavigationSession.MAX_SESSION_POINTS + 1) { stopA }),
                now,
            ),
        )
        assertFalse(NavigationSession.isRestorable(valid.copy(arrivalPromptedIndex = 1), now))
        assertFalse(NavigationSession.isRestorable(valid.copy(lastHandoffAtMillis = now + 10_000L), now))
    }

    @Test
    fun orderedNavigationPointsInterleavesChargersAndVisitsBySegment() {
        // A charger reached at segment 0, then the driver's own stop at segment 1.
        val option = RouteOption(
            id = "r", objective = RouteObjective.FASTEST, supportedObjectives = setOf(RouteObjective.FASTEST),
            title = "t", mode = "m", totalEtaMinutes = 100, drivingMinutes = 80, chargingMinutes = 20,
            detourMinutes = 0, arrivalBatteryPercent = 20, riskScore = 1.0,
            chargingStops = listOf(
                ChargingStop("c1", "Charger", 44.0, -74.0, arrivalBatteryPercent = 20, targetBatteryPercent = 80, chargeDurationMinutes = 20),
            ),
            itinerary = emptyList(),
            stopSegmentIndices = listOf(0),
            userWaypoints = listOf(PlaceCandidate("Lunch", "Main St", 45.0, -75.0, null)),
            userWaypointSegmentIndices = listOf(1),
        )
        val points = option.orderedNavigationPoints()
        assertEquals(listOf("Charger", "Lunch"), points.map { it.name })
        assertEquals(NavigationPoint.Kind.CHARGING, points[0].kind)
        assertEquals(NavigationPoint.Kind.VISIT, points[1].kind)
    }
}
