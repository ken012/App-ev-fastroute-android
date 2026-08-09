package com.evfastroute.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Ports the iOS charger-projection assertions (forward progress + corridor distance). */
class CorridorTest {

    // A route running due east along the equator, lon 0 → 1 (≈111 km).
    private val route = listOf(LatLon(0.0, 0.0), LatLon(0.0, 1.0))

    @Test
    fun chargerOnRouteHasNearZeroCorridorDistanceAndHalfwayProgress() {
        val p = Corridor.project(chargerLat = 0.0, chargerLon = 0.5, points = route)!!
        assertTrue(p.corridorKm < 0.001, "corridor was ${p.corridorKm}")
        assertTrue(abs(p.progressKm - 55.5) < 1.0, "progress was ${p.progressKm}")
    }

    @Test
    fun chargerOffRouteReportsPerpendicularDistance() {
        // 0.1° north of the route ≈ 11.1 km off corridor.
        val p = Corridor.project(chargerLat = 0.1, chargerLon = 0.5, points = route)!!
        assertTrue(abs(p.corridorKm - 11.1) < 0.5, "corridor was ${p.corridorKm}")
    }

    @Test
    fun tooFewPointsReturnsNull() {
        assertNull(Corridor.project(0.0, 0.0, listOf(LatLon(0.0, 0.0))))
    }

    @Test
    fun coveringBoxesPadTheAcceptedCorridorAndNeverDropTheRouteTail() {
        val longRoute = (0..30).map { LatLon(45.0, -80.0 + it * 0.2) }
        val boxes = Corridor.coveringBoxes(longRoute, corridorRadiusKm = 45.0, preferredSegmentKm = 100.0, maxBoxes = 4)
        assertTrue(boxes.size <= 4)
        assertTrue(boxes.first().minLon < longRoute.first().longitude)
        assertTrue(boxes.last().maxLon > longRoute.last().longitude)
        assertTrue(boxes.all { it.maxLat - 45.0 > 0.35 && 45.0 - it.minLat > 0.35 })
    }

    @Test
    fun emptyRouteProducesNoQueryBoxes() {
        assertEquals(emptyList(), Corridor.coveringBoxes(emptyList()))
    }
}
