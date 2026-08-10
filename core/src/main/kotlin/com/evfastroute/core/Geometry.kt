package com.evfastroute.core

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
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

    /** Shortest distance from [point] to a route polyline. This deliberately mirrors the iOS
     * off-route detector: local equirectangular projection per segment, clamped to the segment,
     * then converted back to meters. Empty/single-point geometry is handled conservatively. */
    fun distanceToPolylineMeters(point: LatLon, route: List<LatLon>): Double {
        if (route.isEmpty()) return Double.POSITIVE_INFINITY
        if (route.size == 1) {
            return haversineMeters(point.latitude, point.longitude, route[0].latitude, route[0].longitude)
        }
        var best = Double.POSITIVE_INFINITY
        for (index in 1 until route.size) {
            best = min(best, distanceToSegmentMeters(point, route[index - 1], route[index]))
        }
        return best
    }

    private fun distanceToSegmentMeters(point: LatLon, start: LatLon, end: LatLon): Double {
        val referenceLatitudeRadians = point.latitude * Math.PI / 180.0
        val metersPerDegreeLatitude = 111_320.0
        val metersPerDegreeLongitude = max(1.0, metersPerDegreeLatitude * cos(referenceLatitudeRadians))
        val px = (point.longitude - start.longitude) * metersPerDegreeLongitude
        val py = (point.latitude - start.latitude) * metersPerDegreeLatitude
        val vx = (end.longitude - start.longitude) * metersPerDegreeLongitude
        val vy = (end.latitude - start.latitude) * metersPerDegreeLatitude
        val lengthSquared = vx * vx + vy * vy
        if (lengthSquared <= 0.000001) return sqrt(px * px + py * py)
        val projection = ((px * vx + py * vy) / lengthSquared).coerceIn(0.0, 1.0)
        val dx = px - projection * vx
        val dy = py - projection * vy
        return sqrt(dx * dx + dy * dy)
    }
}
