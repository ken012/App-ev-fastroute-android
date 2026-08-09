package com.evfastroute.core

import kotlin.math.abs
import kotlin.test.Test
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
}
