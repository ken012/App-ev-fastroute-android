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

/** A bounded OCM query tile around one section of a route. */
data class GeoBox(val minLat: Double, val minLon: Double, val maxLat: Double, val maxLon: Double)

object Corridor {

    /**
     * Splits a route into overlapping, padded query boxes. One giant bounding box both wastes OCM
     * results far from the road and silently truncates dense corridors at `maxresults`; a fixed
     * 0.05° pad also misses stations that the planner accepts up to 40 km away. These boxes cover
     * the same corridor the selector evaluates while keeping request count bounded.
     */
    fun coveringBoxes(
        points: List<LatLon>,
        corridorRadiusKm: Double = 45.0,
        preferredSegmentKm: Double = 140.0,
        maxBoxes: Int = 12,
    ): List<GeoBox> {
        if (points.isEmpty()) return emptyList()
        if (points.size == 1) return listOf(boxFor(points, corridorRadiusKm))

        val totalKm = points.zipWithNext().sumOf { (a, b) ->
            Geometry.haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude) / 1_000.0
        }
        val segmentTarget = maxOf(preferredSegmentKm, totalKm / maxBoxes.coerceAtLeast(1))
        val chunks = mutableListOf<List<LatLon>>()
        var current = mutableListOf(points.first())
        var distance = 0.0
        for (index in 1 until points.size) {
            val previous = points[index - 1]
            val point = points[index]
            distance += Geometry.haversineMeters(
                previous.latitude,
                previous.longitude,
                point.latitude,
                point.longitude,
            ) / 1_000.0
            current.add(point)
            if (distance >= segmentTarget && index < points.lastIndex) {
                chunks.add(current)
                current = mutableListOf(point) // overlap at the boundary; no coverage gap
                distance = 0.0
            }
        }
        if (current.size > 1 || chunks.isEmpty()) chunks.add(current)
        val limit = maxBoxes.coerceAtLeast(1)
        while (chunks.size > limit) {
            val tail = chunks.removeAt(chunks.lastIndex)
            val previous = chunks.removeAt(chunks.lastIndex)
            chunks.add(previous + tail.drop(1))
        }
        return chunks.map { boxFor(it, corridorRadiusKm) }
    }

    private fun boxFor(points: List<LatLon>, radiusKm: Double): GeoBox {
        val minLat = points.minOf { it.latitude }
        val maxLat = points.maxOf { it.latitude }
        val minLon = points.minOf { it.longitude }
        val maxLon = points.maxOf { it.longitude }
        val midLatRadians = ((minLat + maxLat) / 2.0) * Math.PI / 180.0
        val latPad = radiusKm.coerceAtLeast(0.0) / 111.0
        val lonPad = radiusKm.coerceAtLeast(0.0) / (111.0 * maxOf(0.1, cos(midLatRadians)))
        return GeoBox(
            minLat = (minLat - latPad).coerceAtLeast(-90.0),
            minLon = (minLon - lonPad).coerceAtLeast(-180.0),
            maxLat = (maxLat + latPad).coerceAtMost(90.0),
            maxLon = (maxLon + lonPad).coerceAtMost(180.0),
        )
    }

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
