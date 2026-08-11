package com.evfastroute.android

import com.evfastroute.android.net.OcmClient
import com.evfastroute.android.net.OrsClient
import com.evfastroute.android.net.ServiceFailure
import com.evfastroute.android.net.ServiceResult
import com.evfastroute.android.net.userMessage
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
import com.evfastroute.core.RangeDrivingStyle
import com.evfastroute.core.RangeEstimator
import com.evfastroute.core.Vehicle
import kotlin.math.roundToInt

// Live trip planning over OpenRouteService + Open Charge Map, mirroring the iOS
// RouteOptimizationService.findRoutes pipeline: direct route → energy check → chargers along the
// corridor → objective-aware beam-search → per-leg road verification → build → optimize. All the
// decision logic is the shared, test-verified :core; this only wires in the network I/O.

class TripPlanner {

    /** Candidate-generation order is intentionally different from display order. Fewest-stops
     * runs first so sequence de-duplication cannot discard its minimum-depth route behind an
     * objective-biased duplicate. This exactly mirrors the iOS optimizer. */
    internal val candidateGenerationObjectives: List<RouteObjective>
        get() = listOf(
            RouteObjective.FEWEST_STOPS,
            RouteObjective.FASTEST,
            RouteObjective.RELIABLE,
            RouteObjective.LOWEST_COST,
        )

    data class Conditions(
        val weatherRangeLossPercent: Double = 0.0,
        val extraLoadKg: Double = 0.0,
        val drivingStyle: RangeDrivingStyle = RangeDrivingStyle.BALANCED,
    )

    data class Preferences(
        val minimumChargerSpeedKw: Int = 50,
        val preferredNetworks: Set<String> = emptySet(),
        val avoidedNetworks: Set<String> = emptySet(),
        val avoidLowConfidenceStations: Boolean = false,
    )

    suspend fun plan(
        start: LatLon,
        destination: LatLon,
        vehicle: Vehicle,
        currentSOC: Double,
        arrivalBufferPercent: Double,
        conditions: Conditions = Conditions(),
        preferences: Preferences = Preferences(),
    ): Result {
        val context = PlanContext()
        val direct = cachedRoute(context, start.latitude, start.longitude, destination.latitude, destination.longitude)
            ?: return Result.Error(context.routeFailure?.userMessage("Driving directions")
                ?: "No drivable route connects those places. Check the selected addresses.")
        val totalDistanceKm = direct.distanceKm
        val directMinutes = direct.durationMinutes
        val routeVehicle = RangeEstimator.planningVehicle(
            from = vehicle,
            currentBatteryPercent = currentSOC,
            arrivalBufferPercent = arrivalBufferPercent,
            weatherRangeLossPercent = conditions.weatherRangeLossPercent,
            extraLoadKg = conditions.extraLoadKg,
            drivingStyle = conditions.drivingStyle,
            averageSpeedKph = RangeEstimator.averageSpeedKph(direct.distanceKm, direct.durationSeconds),
        )

        val energy = EnergyModel.energyPlan(
            distanceKm = totalDistanceKm,
            capacityKwh = routeVehicle.batteryCapacityKwh,
            efficiencyKwhPerKm = routeVehicle.efficiencyKwhPerKm,
            currentBatteryPercent = currentSOC,
            arrivalBufferPercent = arrivalBufferPercent,
        )
        if (!energy.needsCharge) {
            return Result.Success(listOf(directOption(
                directMinutes, energy.arrivalIfNoChargePct, direct.geometry, direct.steps,
                routeVehicle,
            )))
        }

        val chargers = when (val fetched = chargersAlong(direct.geometry)) {
            is ServiceResult.Failure -> return Result.Error(fetched.error.userMessage("Live charging-station data"))
            is ServiceResult.Success -> fetched.value
        }
        if (chargers.isEmpty()) {
            return Result.Error("No live charging stations were reported along this corridor. Try fewer filters or check another route.")
        }

        val projected = chargers.filter { usableCharger(it, routeVehicle, preferences) }.mapNotNull { charger ->
            val projection = Corridor.project(charger.latitude, charger.longitude, direct.geometry) ?: return@mapNotNull null
            if (projection.corridorKm > 40) null
            else ProjectedCharger(charger, projection.progressKm, projection.corridorKm)
        }
        if (projected.isEmpty()) {
            return Result.Error("No compatible stations were close enough to the road corridor.")
        }

        val seen = mutableSetOf<String>()
        val candidates = mutableListOf<RouteOption>()
        for (objective in candidateGenerationObjectives) {
            val prefer = preference(objective, routeVehicle, preferences)
            val sequences = ChargerSequenceSelector.selectChargerSequences(
                projected = projected,
                totalDistanceKm = totalDistanceKm,
                vehicle = routeVehicle,
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
                    key, sequence, start, destination, directMinutes, routeVehicle, currentSOC, arrivalBufferPercent, context,
                )
                if (built != null) candidates.add(built)
            }
        }

        // Fewest-stops guarantee (iOS parity): if the minimum-stop-count sequences all failed road
        // verification, try more at that count before optimize lets a longer route wear the label.
        ensureFewestStops(
            candidates, projected, totalDistanceKm, routeVehicle, currentSOC,
            arrivalBufferPercent, preferences, seen,
        ) { sequence, key ->
            buildFromSequence(key, sequence, start, destination, directMinutes, routeVehicle, currentSOC, arrivalBufferPercent, context)
        }

        val options = RoutePlanner.optimize(candidates)
        return if (options.isEmpty()) {
            Result.Error(context.routeFailure?.userMessage("Driving directions")
                ?: "No verified station sequence can safely span this route. Try fewer filters, a higher starting charge, or a different route.")
        } else {
            Result.Success(options)
        }
    }

    /**
     * Ports iOS findRoutes' fewest-stops fallback: when no built candidate reached the minimum
     * achievable stop count (the leading low-count sequences may have failed the road-routed leg
     * checks), generate more sequences at that same count (skipping already-tried ones) and verify
     * every bounded beam-search candidate at that count, stopping at the first success — so [RoutePlanner.optimize] can't label a
     * higher-stop-count route as "Fewest charging stops" when a shorter one is actually achievable.
     */
    private suspend fun ensureFewestStops(
        candidates: MutableList<RouteOption>,
        projected: List<ProjectedCharger>,
        totalDistanceKm: Double,
        vehicle: Vehicle,
        currentSOC: Double,
        arrivalBufferPercent: Double,
        preferences: Preferences,
        seen: MutableSet<String>,
        build: suspend (sequence: List<Charger>, key: String) -> RouteOption?,
    ) {
        val fewest = ChargerSequenceSelector.selectChargerSequences(
            projected = projected,
            totalDistanceKm = totalDistanceKm,
            vehicle = vehicle,
            currentSOC = currentSOC,
            arrivalBufferPercent = arrivalBufferPercent,
            prefer = preference(RouteObjective.FEWEST_STOPS, vehicle, preferences),
            objective = RouteObjective.FEWEST_STOPS,
            maxSequences = 24,
        )
        val minCount = fewest.minOfOrNull { it.size } ?: return
        if (candidates.any { it.chargingStops.size == minCount }) return
        for (sequence in fewest.filter { it.size == minCount }) {
            val key = ChargerScoring.sequenceKey(sequence.map { it.id })
            if (!seen.add(key)) continue
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
        context: PlanContext,
    ): RouteOption? {
        val points = listOf(start) + sequence.map { LatLon(it.latitude, it.longitude) } + listOf(destination)
        val legs = mutableListOf<RouteLeg>()
        for (index in 0 until points.size - 1) {
            val leg = cachedRoute(
                context,
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
            legSteps = legs.map { it.steps },
        )
    }

    // Candidate charger sequences share many legs (e.g. start→first-charger). Caching per plan()
    // call collapses those duplicates into one OpenRouteService request, which both speeds planning
    // and keeps a single multi-stop plan under the free ORS rate limit (40/min) — without the cache,
    // throttling nulls legs and the user sees a spurious "no plan" failure. Scoped to one plan call.
    private suspend fun cachedRoute(
        context: PlanContext,
        fromLat: Double, fromLon: Double, toLat: Double, toLon: Double,
    ): RouteLeg? {
        val key = "${round5(fromLat)},${round5(fromLon)}>${round5(toLat)},${round5(toLon)}"
        context.legCache[key]?.let { return it }
        if (context.legCache.containsKey(key)) return null // negative result cached — don't refetch this leg
        return when (val result = OrsClient.route(fromLat, fromLon, toLat, toLon)) {
            is ServiceResult.Success -> result.value.also { context.legCache[key] = it }
            is ServiceResult.Failure -> {
                context.routeFailure = context.routeFailure ?: result.error
                context.legCache[key] = null
                null
            }
        }
    }

    private fun round5(value: Double): Long = Math.round(value * 100_000.0)

    // Mirrors iOS findRoutes' pre-selection filter: only consider chargers that are not offline and
    // have at least one connector this vehicle can use. Excluding them BEFORE projection/beam-search
    // stops offline stops being presented and stops incompatible stops crowding out a valid plan.
    private fun usableCharger(charger: Charger, vehicle: Vehicle, preferences: Preferences): Boolean {
        if (charger.status == ChargerStatus.OFFLINE) return false
        if (preferences.avoidLowConfidenceStations && charger.reliabilityScore < 85.0) return false
        if (ChargerScoring.networkMatches(charger.network, preferences.avoidedNetworks)) return false
        val compatiblePower = charger.compatiblePower(vehicle.routingConnectorTypes) ?: return false
        return compatiblePower >= preferences.minimumChargerSpeedKw
    }

    private fun preference(
        objective: RouteObjective,
        vehicle: Vehicle,
        preferences: Preferences,
    ): (Charger) -> Double = when (objective) {
        RouteObjective.FASTEST -> { charger -> ChargerScoring.speedScore(charger, vehicle, preferences.preferredNetworks) }
        RouteObjective.RELIABLE -> { charger -> charger.reliabilityScore }
        RouteObjective.LOWEST_COST -> { charger -> charger.pricePerKwh?.let { -it } ?: Double.NEGATIVE_INFINITY }
        else -> { _ -> 0.0 }
    }

    private fun directOption(
        directMinutes: Int,
        arrivalPct: Int,
        geometry: List<LatLon>,
        steps: List<com.evfastroute.core.DrivingStep>,
        vehicle: Vehicle,
    ): RouteOption = RouteOption(
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
        estimatedConsumptionKwhPer100Km = vehicle.efficiencyKwhPerKm * 100.0,
        routeSteps = steps,
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
        conditions: Conditions = Conditions(),
        preferences: Preferences = Preferences(),
    ): Result {
        if (waypoints.size > MAX_USER_WAYPOINTS) {
            return Result.Error("A trip can include up to $MAX_USER_WAYPOINTS visit stops. Split larger itineraries into separate trips.")
        }
        if (waypoints.isEmpty()) {
            return plan(start, destination, vehicle, currentSOC, arrivalBufferPercent, conditions, preferences)
        }

        val context = PlanContext()
        val points = listOf(start) + waypoints.map { LatLon(it.latitude, it.longitude) } + listOf(destination)
        val legRoutes = mutableListOf<RouteLeg>()
        for (i in 0 until points.size - 1) {
            val leg = cachedRoute(context, points[i].latitude, points[i].longitude, points[i + 1].latitude, points[i + 1].longitude)
                ?: return Result.Error(context.routeFailure?.userMessage("Driving directions")
                    ?: "No drivable route connects every selected stop. Check the stop order and addresses.")
            legRoutes.add(leg)
        }

        val combinedGeometry = legRoutes.flatMap { it.geometry }
        val legStartProgressKm = mutableListOf<Double>()
        var cumulative = 0.0
        for (leg in legRoutes) { legStartProgressKm.add(cumulative); cumulative += leg.distanceKm }
        val totalDistanceKm = cumulative
        val directMinutes = legRoutes.sumOf { it.durationMinutes }
        val routeVehicle = RangeEstimator.planningVehicle(
            from = vehicle,
            currentBatteryPercent = currentSOC,
            arrivalBufferPercent = arrivalBufferPercent,
            weatherRangeLossPercent = conditions.weatherRangeLossPercent,
            extraLoadKg = conditions.extraLoadKg,
            drivingStyle = conditions.drivingStyle,
            averageSpeedKph = RangeEstimator.averageSpeedKph(
                totalDistanceKm,
                legRoutes.sumOf { it.durationSeconds },
            ),
        )
        // Each user waypoint sits at a leg boundary: waypoint k is the arrival of leg k.
        val waypointProgress = waypoints.indices.map { legStartProgressKm[it + 1] }

        val energy = EnergyModel.energyPlan(
            distanceKm = totalDistanceKm, capacityKwh = routeVehicle.batteryCapacityKwh,
            efficiencyKwhPerKm = routeVehicle.efficiencyKwhPerKm, currentBatteryPercent = currentSOC,
            arrivalBufferPercent = arrivalBufferPercent,
        )
        if (!energy.needsCharge) {
            return Result.Success(
                listOf(
                    directThroughOption(
                        directMinutes, legRoutes, waypoints, waypointProgress,
                        routeVehicle, currentSOC, energy.arrivalIfNoChargePct, combinedGeometry,
                    ),
                ),
            )
        }

        val chargers = when (val fetched = chargersAlong(combinedGeometry)) {
            is ServiceResult.Failure -> return Result.Error(fetched.error.userMessage("Live charging-station data"))
            is ServiceResult.Success -> fetched.value
        }
        if (chargers.isEmpty()) {
            return Result.Error("No live charging stations were reported along this corridor. Try fewer filters or check another route.")
        }
        val projected = chargers.filter { usableCharger(it, routeVehicle, preferences) }.mapNotNull { charger ->
            val projection = Corridor.project(charger.latitude, charger.longitude, combinedGeometry) ?: return@mapNotNull null
            if (projection.corridorKm > 40) null else ProjectedCharger(charger, projection.progressKm, projection.corridorKm)
        }
        if (projected.isEmpty()) {
            return Result.Error("No compatible stations were close enough to the road corridor.")
        }
        val progressById = projected.associate { it.charger.id to it.progressKm }

        val seen = mutableSetOf<String>()
        val candidates = mutableListOf<RouteOption>()
        for (objective in candidateGenerationObjectives) {
            val prefer = preference(objective, routeVehicle, preferences)
            val sequences = ChargerSequenceSelector.selectChargerSequences(
                projected = projected,
                totalDistanceKm = totalDistanceKm,
                vehicle = routeVehicle,
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
                    directMinutes, routeVehicle, currentSOC, arrivalBufferPercent, context,
                )
                if (built != null) candidates.add(built)
            }
        }

        ensureFewestStops(
            candidates, projected, totalDistanceKm, routeVehicle, currentSOC,
            arrivalBufferPercent, preferences, seen,
        ) { sequence, key ->
            buildThroughSequence(
                key, sequence, start, destination, waypoints, waypointProgress, progressById,
                directMinutes, routeVehicle, currentSOC, arrivalBufferPercent, context,
            )
        }

        val options = RoutePlanner.optimize(candidates)
        return if (options.isEmpty()) {
            Result.Error(context.routeFailure?.userMessage("Driving directions")
                ?: "No verified station sequence can safely span this route. Try fewer filters or a higher starting charge.")
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
        context: PlanContext,
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
                context,
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
            legSteps = legs.map { it.steps },
        )
    }

    private fun directThroughOption(
        directMinutes: Int,
        legRoutes: List<RouteLeg>,
        waypoints: List<PlaceCandidate>,
        waypointProgress: List<Double>,
        vehicle: Vehicle,
        currentSOC: Double,
        finalArrivalBatteryPercent: Int,
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
            arrivalBatteryPercent = finalArrivalBatteryPercent,
            riskScore = 1.0,
            chargingStops = emptyList(),
            itinerary = itinerary,
            geometry = geometry,
            estimatedConsumptionKwhPer100Km = vehicle.efficiencyKwhPerKm * 100.0,
            userWaypoints = waypoints,
            userWaypointSegmentIndices = waypoints.indices.toList(),
            routeSteps = legRoutes.flatMapIndexed { segmentIndex, leg ->
                leg.steps.map { it.copy(segmentIndex = segmentIndex) }
            },
        )
    }

    private suspend fun chargersAlong(geometry: List<LatLon>): ServiceResult<List<Charger>> {
        val boxes = Corridor.coveringBoxes(geometry)
        if (boxes.isEmpty()) {
            return ServiceResult.Failure(
                com.evfastroute.android.net.ServiceFailure(
                    com.evfastroute.android.net.ServiceFailureKind.INVALID_RESPONSE,
                ),
            )
        }
        val chargers = linkedMapOf<String, Charger>()
        var successfulRequests = 0
        var firstFailure: ServiceFailure? = null
        for (box in boxes) {
            when (val response = OcmClient.chargers(box.minLat, box.minLon, box.maxLat, box.maxLon)) {
                is ServiceResult.Success -> {
                    successfulRequests++
                    response.value.forEach { charger -> chargers.putIfAbsent(charger.id, charger) }
                }
                is ServiceResult.Failure -> {
                    firstFailure = firstFailure ?: response.error
                    if (response.error.kind == com.evfastroute.android.net.ServiceFailureKind.CONFIGURATION ||
                        response.error.kind == com.evfastroute.android.net.ServiceFailureKind.UNAUTHORIZED
                    ) break
                }
            }
        }
        return if (successfulRequests == 0 && firstFailure != null) {
            ServiceResult.Failure(firstFailure)
        } else {
            ServiceResult.Success(chargers.values.toList())
        }
    }

    private data class PlanContext(
        val legCache: MutableMap<String, RouteLeg?> = HashMap(),
        var routeFailure: ServiceFailure? = null,
    )

    sealed interface Result {
        data class Success(val options: List<RouteOption>) : Result
        data class Error(val message: String) : Result
    }
}
