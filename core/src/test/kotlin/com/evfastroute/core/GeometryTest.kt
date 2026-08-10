package com.evfastroute.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Ports the iOS haversine assertion so distance math agrees across platforms. */
class GeometryTest {

    @Test
    fun oneDegreeLatitudeIsAbout111Km() {
        val d = Geometry.haversineMeters(0.0, 0.0, 1.0, 0.0)
        assertTrue(abs(d - 111_195.0) < 1_000.0, "expected ~111195 m, got $d")
    }

    @Test
    fun samePointIsZero() {
        assertTrue(Geometry.haversineMeters(45.0, -74.0, 45.0, -74.0) < 0.001)
    }

    @Test
    fun distanceToPolylineUsesTheNearestClampedSegment() {
        val route = listOf(LatLon(45.0, -74.0), LatLon(45.0, -73.0))
        val nearMiddle = Geometry.distanceToPolylineMeters(LatLon(45.001, -73.5), route)
        assertTrue(nearMiddle in 100.0..125.0, "expected about 111 m, got $nearMiddle")

        val beyondEnd = Geometry.distanceToPolylineMeters(LatLon(45.0, -72.9), route)
        assertTrue(beyondEnd > 7_000.0, "projection must clamp to the route endpoint")
        assertEquals(Double.POSITIVE_INFINITY, Geometry.distanceToPolylineMeters(LatLon(0.0, 0.0), emptyList()))
    }
}
