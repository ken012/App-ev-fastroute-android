package com.evfastroute.core

import kotlin.math.cos
import kotlin.math.hypot

// Projects a charger onto the driven route corridor. Faithful port of iOS
// RouteOptimizationService.project(charger:onto:). Uses an equirectangular (km-scaled)
// approximation good for the short spans between polyline samples.

/** A geographic point. Mirrors CLLocationCoordinate2D for the pure-logic layer. */
data class LatLon(val latitude: Double, val longitude: Double)

/** Where a charger sits relative to a route: distance travelled along it, and how far off it. */
data class Projection(val progressKm: Double, val corridorKm: Double)

object Corridor {

    fun project(chargerLat: Double, chargerLon: Double, points: List<LatLon>): Projection? {
        if (points.size <= 1) return null
        var cumulative = 0.0
        var bestDistance = Double.MAX_VALUE
        var bestProgress = 0.0
        for (index in 1 until points.size) {
            val a = points[index - 1]
            val b = points[index]
            val midLat = (a.latitude + b.latitude) / 2 * Math.PI / 180
            val lonScale = maxOf(0.01, cos(midLat)) * 111
            val latScale = 111.0
            val px = chargerLon * lonScale
            val py = chargerLat * latScale
            val ax = a.longitude * lonScale
            val ay = a.latitude * latScale
            val bx = b.longitude * lonScale
            val by = b.latitude * latScale
            val dx = bx - ax
            val dy = by - ay
            val lengthSquared = dx * dx + dy * dy
            val t = if (lengthSquared > 0) {
                minOf(1.0, maxOf(0.0, ((px - ax) * dx + (py - ay) * dy) / lengthSquared))
            } else {
                0.0
            }
            val distance = hypot(px - (ax + t * dx), py - (ay + t * dy))
            val segmentKm = hypot(dx, dy)
            if (distance < bestDistance) {
                bestDistance = distance
                bestProgress = cumulative + segmentKm * t
            }
            cumulative += segmentKm
        }
        return Projection(progressKm = bestProgress, corridorKm = bestDistance)
    }
}
