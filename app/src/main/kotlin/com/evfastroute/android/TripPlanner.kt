package com.evfastroute.android

import com.evfastroute.android.net.OcmClient
import com.evfastroute.android.net.OrsClient
import com.evfastroute.core.Charger
import com.evfastroute.core.ChargerScoring
import com.evfastroute.core.ChargerStatus
import com.evfastroute.core.ChargerSequenceSelector
import com.evfastroute.core.Corridor
import com.evfastroute.core.EnergyModel
import com.evfastroute.core.ItineraryStop
import com.evfastroute.core.LatLon
import com.evfastroute.core.PlaceCandidate
import com.evfastroute.core.PlannedVia
import com.evfastroute.core.ProjectedCharger
import com.evfastroute.core.RouteLeg
import com.evfastroute.core.RouteObjective
import com.evfastroute.core.RouteOption
import com.evfastroute.core.RoutePlanner
import com.evfastroute.core.Vehicle
import kotlin.math.roundToInt

// Live trip planning over OpenRouteService + Open Charge Map, mirroring the iOS
// RouteOptimizationService.findRoutes pipeline: direct route → energy check → chargers along the
// corridor → objective-aware beam-search → per-leg road verification → build → optimize. All the
// decision logic is the shared, test-verified :core; this only wires in the network I/O.

class TripPlanner {

    suspend fun plan(
        start: LatLon,
        destination: LatLon,
        vehicle: Vehicle,
        currentSOC: Double,
        arrivalBufferPercent: Double,
    ): Result {
        val legCache = HashMap<String, RouteLeg?>()
        val direct = cachedRoute(legCache, start.latitude, start.longitude, destination.latitude, destination.longitude)
            ?: return Result.Error("Couldn't calculate a driving route. Check the addresses and your connection.")
        val totalDistanceKm = direct.distanceKm
        val directMinutes = direct.durationMinutes

        val energy = EnergyModel.energyPlan(
            distanceKm = totalDistanceKm,
            capacityKwh = vehicle.batteryCapacityKwh,
            efficiencyKwhPerKm = vehicle.efficiencyKwhPerKm,
            currentBatteryPercent = currentSOC,
            arrivalBufferPercent = arrivalBufferPercent,
        )
        if (!energy.needsCharge) {
            return Result.Success(listOf(directOption(directMinutes, energy.arrivalIfNoChargePct, direct.geometry)))
        }

        val box = boundingBox(direct.geometry) ?: return Result.Error("No route geometry was returned.")
        val chargers = OcmClient.chargers(box.minLat, box.minLon, box.maxLat, box.maxLon)
        if (chargers.isEmpty()) {
            return Result.Error("No live charging stations were found along this route (is the OCM key set?).")
        }

        val projected = chargers.filter { usableCharger(it, vehicle) }.mapNotNull { charger ->
            val projection = Corridor.project(charger.latitude, charger.longitude, direct.geometry) ?: return@mapNotNull null
            if (projection.corridorKm > 40) null
            else ProjectedCharger(charger, projection.progressKm, projection.corridorKm)
        }
        if (projected.isEmpty()) {
            return Result.Error("No compatible stations were close enough to the road corridor.")
        }

        val seen = mutableSetOf<String>()
        val candidates = mutableListOf<RouteOption>()
        for (objective in RouteObjective.plannerCases) {
            val prefer = preference(objective, vehicle)
            val sequences = ChargerSequenceSelector.selectChargerSequences(
                projected = projected,
                totalDistanceKm = totalDistanceKm,
                vehicle = vehicle,
                currentSOC = currentSOC,
                arrivalBufferPercent = arrivalBufferPercent,
                prefer = prefer,
                objective = objective,
                maxSequences = if (objective == RouteObjective.FEWEST_STOPS) 16 else 10,
            )
            val quota = if (objective == RouteObjective.FEWEST_STOPS) 4 else 2
            for (sequence in sequences.take(quota)) {
                val key = ChargerScoring.sequenceKey(sequence.map { it.id })
                if (!seen.add(key)) continue
                val built = buildFromSequence(
                    key, sequence, start, destination, directMinutes, vehicle, currentSOC, arrivalBufferPercent, legCache,
                )
                if (built != null) candidates.add(built)
            }
        }

        // Fewest-stops guarantee (iOS parity): if the minimum-stop-count sequences all failed road
        // verification, try more at that count before optimize lets a longer route wear the label.
        ensureFewestStops(candidates, projected, totalDistanceKm, vehicle, currentSOC, arrivalBufferPercent, seen) { sequence, key ->
            buildFromSequence(key, sequence, start, destination, directMinutes, vehicle, currentSOC, arrivalBufferPercent, legCache)
        }

        val options = RoutePlanner.optimize(candidates)
        return if (options.isEmpty()) {
            Result.Error("No safe charging plan spans this route. Try a lower minimum charger speed or more starting charge.")
        } else {
            Result.Success(options)
        }
    }

    /**
     * Ports iOS findRoutes' fewest-stops fallback: when no built candidate reached the minimum
     * achievable stop count (the leading low-count sequences may have failed the road-routed leg
     * checks), generate more sequences at that same count (skipping already-tried ones) and build up
     * to 6 more, stopping at the first success — so [RoutePlanner.optimize] can't label a
     * higher-stop-count route as "Fewest charging stops" when a shorter one is actually achievable.
     */
    private suspend fun ensureFewestStops(
        candidates: MutableList<RouteOption>,
        projected: List<ProjectedCharger>,
        totalDistanceKm: Double,
        vehicle: Vehicle,
        currentSOC: Double,
        arrivalBufferPercent: Double,
        seen: MutableSet<String>,
        build: suspend (sequence: List<Charger>, key: String) -> RouteOption?,
    ) {
        val fewest = ChargerSequenceSelector.selectChargerSequences(
            projected = projected,
            totalDistanceKm = totalDistanceKm,
            vehicle = vehicle,
            currentSOC = currentSOC,
            arrivalBufferPercent = arrivalBufferPercent,
            prefer = preference(RouteObjective.FEWEST_STOPS, vehicle),
            objective = RouteObjective.FEWEST_STOPS,
            maxSequences = 24,
        )
        val minCount = fewest.minOfOrNull { it.size } ?: return
        if (candidates.any { it.chargingStops.size == minCount }) return
        var attempts = 0
        for (sequence in fewest.filter { it.size == minCount }) {
            if (attempts >= 6) break
            val key = ChargerScoring.sequenceKey(sequence.map { it.id })
            if (!seen.add(key)) continue
            attempts++
            val built = build(sequence, key)
            if (built != null) { candidates.add(built); break }
        }
    }

    private suspend fun buildFromSequence(
        id: String,
        sequence: List<Charger>,
        start: LatLon,
        destination: LatLon,
        directMinutes: Int,
        vehicle: Vehicle,
        currentSOC: Double,
        arrivalBufferPercent: Double,
        legCache: MutableMap<String, RouteLeg?>,
    ): RouteOption? {
        val points = listOf(start) + sequence.map { LatLon(it.latitude, it.longitude) } + listOf(destination)
        val legs = mutableListOf<RouteLeg>()
        for (index in 0 until points.size - 1) {
            val leg = cachedRoute(
                legCache,
                points[index].latitude, points[index].longitude,
                points[index + 1].latitude, points[index + 1].longitude,
            ) ?: return null
            legs.add(leg)
        }
        return RoutePlanner.buildRoute(
            id = id,
            sequence = sequence,
            legDistancesKm = legs.map { it.distanceKm },
            legDurationMinutes = legs.map { it.durationMinutes },
            directMinutes = directMinutes,
            vehicle = vehicle,
            currentSOC = currentSOC,
            arrivalBufferPercent = arrivalBufferPercent,
            legGeometries = legs.map { it.geometry },
        )
    }

    // Candidate charger sequences share many legs (e.g. start→first-charger). Caching per plan()
    // call collapses those duplicates into one OpenRouteService request, which both speeds planning
    // and keeps a single multi-stop plan under the free ORS rate limit (40/min) — without the cache,
    // throttling nulls legs and the user sees a spurious "no plan" failure. Scoped to one plan call.
    private suspend fun cachedRoute(
        cache: MutableMap<String, RouteLeg?>,
        fromLat: Double, fromLon: Double, toLat: Double, toLon: Double,
    ): RouteLeg? {
        val key = "${round5(fromLat)},${round5(fromLon)}>${round5(toLat)},${round5(toLon)}"
        cache[key]?.let { return it }
        if (cache.containsKey(key)) return null // negative result cached — don't refetch this leg
        return OrsClient.route(fromLat, fromLon, toLat, toLon).also { cache[key] = it }
    }

    private fun round5(value: Double): Long = Math.round(value * 100_000.0)

    // Mirrors iOS findRoutes' pre-selection filter: only consider chargers that are not offline and
    // have at least one connector this vehicle can use. Excluding them BEFORE projection/beam-search
    // stops offline stops being presented and stops incompatible stops crowding out a valid plan.
    private fun usableCharger(charger: Charger, vehicle: Vehicle): Boolean =
        charger.status != ChargerStatus.OFFLINE && charger.compatiblePower(vehicle.connectorTypes) != null

    private fun preference(objective: RouteObjective, vehicle: Vehicle): (Charger) -> Double = when (objective) {
        RouteObjective.FASTEST -> { charger -> ChargerScoring.speedScore(charger, vehicle, emptySet()) }
        RouteObjective.RELIABLE -> { charger -> charger.reliabilityScore }
        RouteObjective.LOWEST_COST -> { charger -> -charger.pricePerKwh }
        else -> { _ -> 0.0 }
    }

    private fun directOption(directMinutes: Int, arrivalPct: Int, geometry: List<LatLon>): RouteOption = RouteOption(
        id = "direct",
        objective = RouteObjective.DIRECT,
        supportedObjectives = setOf(RouteObjective.DIRECT),
        title = RouteObjective.DIRECT.title,
        mode = RouteObjective.DIRECT.mode,
        totalEtaMinutes = directMinutes,
        drivingMinutes = directMinutes,
        chargingMinutes = 0,
        detourMinutes = 0,
        arrivalBatteryPercent = arrivalPct,
        riskScore = 1.0,
        chargingStops = emptyList(),
        itinerary = emptyList(),
        geometry = geometry,
    )

    /**
     * Plans a trip that drives THROUGH the driver's own ordered [waypoints], optimizing charging
     * globally across the whole multi-leg corridor (chargers inserted only where the trip needs
     * them, spanning the visits). Mirrors the iOS through-waypoints path. Falls back to [plan] when
     * there are no interior stops.
     */
    suspend fun planThrough(
        start: LatLon,
        waypoints: List<PlaceCandidate>,
        destination: LatLon,
        vehicle: Vehicle,
        currentSOC: Double,
        arrivalBufferPercent: Double,
    ): Result {
        if (waypoints.isEmpty()) return plan(start, destination, vehicle, currentSOC, arrivalBufferPercent)

        val legCache = HashMap<String, RouteLeg?>()
        val points = listOf(start) + waypoints.map { LatLon(it.latitude, it.longitude) } + listOf(destination)
        val legRoutes = mutableListOf<RouteLeg>()
        for (i in 0 until points.size - 1) {
            val leg = cachedRoute(legCache, points[i].latitude, points[i].longitude, points[i + 1].latitude, points[i + 1].longitude)
                ?: return Result.Error("Couldn't calculate a driving route through your stops. Check the addresses and your connection.")
            legRoutes.add(leg)
        }

        val combinedGeometry = legRoutes.flatMap { it.geometry }
        val legStartProgressKm = mutableListOf<Double>()
        var cumulative = 0.0
        for (leg in legRoutes) { legStartProgressKm.add(cumulative); cumulative += leg.distanceKm }
        val totalDistanceKm = cumulative
        val directMinutes = legRoutes.sumOf { it.durationMinutes }
        // Each user waypoint sits at a leg boundary: waypoint k is the arrival of leg k.
        val waypointProgress = waypoints.indices.map { legStartProgressKm[it + 1] }

        val energy = EnergyModel.energyPlan(
            distanceKm = totalDistanceKm, capacityKwh = vehicle.batteryCapacityKwh,
            efficiencyKwhPerKm = vehicle.efficiencyKwhPerKm, currentBatteryPercent = currentSOC,
            arrivalBufferPercent = arrivalBufferPercent,
        )
        if (!energy.needsCharge) {
            return Result.Success(
                listOf(
                    directThroughOption(
                        directMinutes, legRoutes, waypoints, waypointProgress, totalDistanceKm,
                        vehicle, currentSOC, combinedGeometry,
                    ),
                ),
            )
        }

        val box = boundingBox(combinedGeometry) ?: return Result.Error("No route geometry was returned.")
        val chargers = OcmClient.chargers(box.minLat, box.minLon, box.maxLat, box.maxLon)
        if (chargers.isEmpty()) {
            return Result.Error("No live charging stations were found along this route (is the OCM key set?).")
        }
        val projected = chargers.filter { usableCharger(it, vehicle) }.mapNotNull { charger ->
            val projection = Corridor.project(charger.latitude, charger.longitude, combinedGeometry) ?: return@mapNotNull null
            if (projection.corridorKm > 40) null else ProjectedCharger(charger, projection.progressKm, projection.corridorKm)
        }
        if (projected.isEmpty()) {
            return Result.Error("No compatible stations were close enough to the road corridor.")
        }
        val progressById = projected.associate { it.charger.id to it.progressKm }

        val seen = mutableSetOf<String>()
        val candidates = mutableListOf<RouteOption>()
        for (objective in RouteObjective.plannerCases) {
            val prefer = preference(objective, vehicle)
            val sequences = ChargerSequenceSelector.selectChargerSequences(
                projected = projected,
                totalDistanceKm = totalDistanceKm,
                vehicle = vehicle,
                currentSOC = currentSOC,
                arrivalBufferPercent = arrivalBufferPercent,
                prefer = prefer,
                objective = objective,
                maxSequences = if (objective == RouteObjective.FEWEST_STOPS) 16 else 10,
            )
            val quota = if (objective == RouteObjective.FEWEST_STOPS) 4 else 2
            for (sequence in sequences.take(quota)) {
                val key = ChargerScoring.sequenceKey(sequence.map { it.id })
                if (!seen.add(key)) continue
                val built = buildThroughSequence(
                    key, sequence, start, destination, waypoints, waypointProgress, progressById,
                    directMinutes, vehicle, currentSOC, arrivalBufferPercent, legCache,
                )
                if (built != null) candidates.add(built)
            }
        }

        ensureFewestStops(candidates, projected, totalDistanceKm, vehicle, currentSOC, arrivalBufferPercent, seen) { sequence, key ->
            buildThroughSequence(
                key, sequence, start, destination, waypoints, waypointProgress, progressById,
                directMinutes, vehicle, currentSOC, arrivalBufferPercent, legCache,
            )
        }

        val options = RoutePlanner.optimize(candidates)
        return if (options.isEmpty()) {
            Result.Error("No safe charging plan spans this route. Try a lower minimum charger speed or more starting charge.")
        } else {
            Result.Success(options)
        }
    }

    private suspend fun buildThroughSequence(
        id: String,
        sequence: List<Charger>,
        start: LatLon,
        destination: LatLon,
        waypoints: List<PlaceCandidate>,
        waypointProgress: List<Double>,
        progressById: Map<String, Double>,
        directMinutes: Int,
        vehicle: Vehicle,
        currentSOC: Double,
        arrivalBufferPercent: Double,
        legCache: MutableMap<String, RouteLeg?>,
    ): RouteOption? {
        data class OrderedVia(val via: PlannedVia, val progressKm: Double, val point: LatLon)

        val vias = mutableListOf<OrderedVia>()
        waypoints.forEachIndexed { index, wp ->
            vias.add(
                OrderedVia(
                    PlannedVia(charger = null, userWaypointIndex = index, name = wp.placeName),
                    waypointProgress[index], LatLon(wp.latitude, wp.longitude),
                ),
            )
        }
        sequence.forEach { charger ->
            vias.add(
                OrderedVia(
                    PlannedVia(charger = charger, userWaypointIndex = null, name = charger.name),
                    progressById[charger.id] ?: 0.0, LatLon(charger.latitude, charger.longitude),
                ),
            )
        }
        // Deterministic order: by corridor progress; at a tie, the driver's own stop first, then name.
        val ordered = vias.sortedWith(
            compareBy({ it.progressKm }, { if (it.via.userWaypointIndex != null) 0 else 1 }, { it.via.name }),
        )
        val orderedPoints = listOf(start) + ordered.map { it.point } + listOf(destination)
        val legs = mutableListOf<RouteLeg>()
        for (i in 0 until orderedPoints.size - 1) {
            val leg = cachedRoute(
                legCache,
                orderedPoints[i].latitude, orderedPoints[i].longitude,
                orderedPoints[i + 1].latitude, orderedPoints[i + 1].longitude,
            ) ?: return null
            legs.add(leg)
        }
        return RoutePlanner.buildRouteThroughWaypoints(
            id = id,
            vias = ordered.map { it.via },
            userWaypoints = waypoints,
            legDistancesKm = legs.map { it.distanceKm },
            legDurationMinutes = legs.map { it.durationMinutes },
            directMinutes = directMinutes,
            vehicle = vehicle,
            currentSOC = currentSOC,
            arrivalBufferPercent = arrivalBufferPercent,
            legGeometries = legs.map { it.geometry },
        )
    }

    private fun directThroughOption(
        directMinutes: Int,
        legRoutes: List<RouteLeg>,
        waypoints: List<PlaceCandidate>,
        waypointProgress: List<Double>,
        totalDistanceKm: Double,
        vehicle: Vehicle,
        currentSOC: Double,
        geometry: List<LatLon>,
    ): RouteOption {
        val capacity = maxOf(1.0, vehicle.batteryCapacityKwh)
        val socPerKm = vehicle.efficiencyKwhPerKm / capacity * 100
        val itinerary = mutableListOf<ItineraryStop>()
        var elapsed = 0
        waypoints.forEachIndexed { k, wp ->
            elapsed += legRoutes[k].durationMinutes
            val batt = (currentSOC - waypointProgress[k] * socPerKm).roundToInt().coerceIn(0, 100)
            itinerary.add(ItineraryStop(wp.placeName, ItineraryStop.Kind.VISIT, elapsed, batt))
        }
        val arrivalPct = (currentSOC - totalDistanceKm * socPerKm).roundToInt().coerceIn(0, 100)
        return RouteOption(
            id = "direct",
            objective = RouteObjective.DIRECT,
            supportedObjectives = setOf(RouteObjective.DIRECT),
            title = RouteObjective.DIRECT.title,
            mode = RouteObjective.DIRECT.mode,
            totalEtaMinutes = directMinutes,
            drivingMinutes = directMinutes,
            chargingMinutes = 0,
            detourMinutes = 0,
            arrivalBatteryPercent = arrivalPct,
            riskScore = 1.0,
            chargingStops = emptyList(),
            itinerary = itinerary,
            geometry = geometry,
            userWaypoints = waypoints,
            userWaypointSegmentIndices = waypoints.indices.toList(),
        )
    }

    private data class Box(val minLat: Double, val minLon: Double, val maxLat: Double, val maxLon: Double)

    private fun boundingBox(points: List<LatLon>): Box? {
        if (points.isEmpty()) return null
        var minLat = points.first().latitude
        var maxLat = minLat
        var minLon = points.first().longitude
        var maxLon = minLon
        for (point in points) {
            minLat = minOf(minLat, point.latitude); maxLat = maxOf(maxLat, point.latitude)
            minLon = minOf(minLon, point.longitude); maxLon = maxOf(maxLon, point.longitude)
        }
        val pad = 0.05 // ~5 km, so corridor-adjacent chargers are included
        return Box(minLat - pad, minLon - pad, maxLat + pad, maxLon + pad)
    }

    sealed interface Result {
        data class Success(val options: List<RouteOption>) : Result
        data class Error(val message: String) : Result
    }
}
