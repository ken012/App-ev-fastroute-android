package com.evfastroute.android

import com.evfastroute.android.net.OcmClient
import com.evfastroute.android.net.OrsClient
import com.evfastroute.core.Charger
import com.evfastroute.core.ChargerScoring
import com.evfastroute.core.ChargerSequenceSelector
import com.evfastroute.core.Corridor
import com.evfastroute.core.EnergyModel
import com.evfastroute.core.LatLon
import com.evfastroute.core.ProjectedCharger
import com.evfastroute.core.RouteLeg
import com.evfastroute.core.RouteObjective
import com.evfastroute.core.RouteOption
import com.evfastroute.core.RoutePlanner
import com.evfastroute.core.Vehicle

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
        val direct = OrsClient.route(start.latitude, start.longitude, destination.latitude, destination.longitude)
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

        val projected = chargers.mapNotNull { charger ->
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
                    key, sequence, start, destination, directMinutes, vehicle, currentSOC, arrivalBufferPercent,
                )
                if (built != null) candidates.add(built)
            }
        }

        val options = RoutePlanner.optimize(candidates)
        return if (options.isEmpty()) {
            Result.Error("No safe charging plan spans this route. Try a lower minimum charger speed or more starting charge.")
        } else {
            Result.Success(options)
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
    ): RouteOption? {
        val points = listOf(start) + sequence.map { LatLon(it.latitude, it.longitude) } + listOf(destination)
        val legs = mutableListOf<RouteLeg>()
        for (index in 0 until points.size - 1) {
            val leg = OrsClient.route(
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
