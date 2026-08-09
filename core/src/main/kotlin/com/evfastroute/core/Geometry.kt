package com.evfastroute.core

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Great-circle distance. Behavior-identical to the iOS NavigationTracker/haversine helper so
// arrival detection, corridor projection and range math agree across platforms.
object Geometry {

    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = lat1 * Math.PI / 180.0
        val phi2 = lat2 * Math.PI / 180.0
        val deltaPhi = (lat2 - lat1) * Math.PI / 180.0
        val deltaLambda = (lon2 - lon1) * Math.PI / 180.0
        val a = sin(deltaPhi / 2) * sin(deltaPhi / 2) +
            cos(phi1) * cos(phi2) * sin(deltaLambda / 2) * sin(deltaLambda / 2)
        return EARTH_RADIUS_METERS * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
