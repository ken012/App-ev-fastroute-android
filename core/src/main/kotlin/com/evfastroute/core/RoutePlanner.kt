package com.evfastroute.core

import kotlin.math.ceil
import kotlin.math.roundToInt

// Pure routing orchestration. Given a charger sequence and the per-leg distances/durations that
// the shared OpenRouteService contract returns, it walks the
// state-of-charge to build a RouteOption; and given several built candidates it selects the best
// per objective and merges duplicates. Network I/O lives in the :app service clients; this stays
// pure and test-verified against the iOS behavior.

/** One point the trip drives through, in travel order: either a charging stop (charger != null) or
 * one of the driver's own waypoints (userWaypointIndex != null). Mirrors the iOS `Via`. */
data class PlannedVia(
    val charger: Charger?,
    val userWaypointIndex: Int?,
    val name: String,
)

object RoutePlanner {

    /**
     * Builds one route from an ordered charger sequence. `legDistancesKm`/`legDurationMinutes`
     * describe start→c0, c0→c1, …, cN→destination (size = sequence.size + 1). Returns null if the
     * sequence is not energy-feasible. The objective is assigned later by [optimize].
     */
    fun buildRoute(
        id: String,
        sequence: List<Charger>,
        legDistancesKm: List<Double>,
        legDurationMinutes: List<Int>,
        directMinutes: Int,
        vehicle: Vehicle,
        currentSOC: Double,
        arrivalBufferPercent: Double,
        legGeometries: List<List<LatLon>> = emptyList(),
        legSteps: List<List<DrivingStep>> = emptyList(),
    ): RouteOption? {
        if (legDistancesKm.size != sequence.size + 1 || legDurationMinutes.size != sequence.size + 1) return null
        val capacity = maxOf(1.0, vehicle.batteryCapacityKwh)
        val socPerKm = vehicle.efficiencyKwhPerKm / capacity * 100

        var soc = currentSOC
        val stops = mutableListOf<ChargingStop>()
        val itinerary = mutableListOf<ItineraryStop>()
        var elapsedMinutes = 0
        var chargingMinutes = 0
        var totalCost = 0.0
        var costCurrency: String? = null
        var hasCompleteCost = true
        var reliabilitySum = 0.0

        for (index in sequence.indices) {
            val charger = sequence[index]
            val arrival = soc - legDistancesKm[index] * socPerKm
            if (arrival < 5) return null
            elapsedMinutes += legDurationMinutes[index]

            val nextKm = legDistancesKm[index + 1]
            val reserve = if (index == sequence.size - 1) arrivalBufferPercent else 10.0
            val requiredDeparture = nextKm * socPerKm + reserve
            if (requiredDeparture > 95) return null
            // Charge only as much as the next leg needs (no artificial floor) — matches iOS
            // buildSegmentedRoute and this file's buildRouteThroughWaypoints.
            val target = minOf(95.0, maxOf(arrival, requiredDeparture))
            val arrivalInt = arrival.roundToInt().coerceIn(0, 100)
            val targetInt = maxOf(arrivalInt, minOf(95, ceil(target).toInt()))
            // A charger reached with enough charge to continue is not a real stop — drop the whole
            // candidate rather than emit a phantom zero-charge stop (iOS guards the same way).
            if (targetInt <= arrivalInt) return null
            val compatibleKw = charger.compatiblePower(vehicle.routingConnectorTypes) ?: return null
            val effectiveKw = maxOf(1.0, minOf(compatibleKw.toDouble(), vehicle.maxDcChargingKw.toDouble()))
            val minutes = ChargePlanner.chargeMinutes(arrivalInt, targetInt, capacity, effectiveKw)

            itinerary.add(
                ItineraryStop(charger.name, ItineraryStop.Kind.CHARGING, elapsedMinutes, arrivalInt),
            )
            elapsedMinutes += minutes
            chargingMinutes += minutes
            val price = charger.pricePerKwh
            val currency = charger.priceCurrencyCode
            if (price == null || currency == null || (costCurrency != null && costCurrency != currency)) {
                hasCompleteCost = false
            } else {
                costCurrency = currency
                totalCost += ChargePlanner.energyAdded(arrivalInt, targetInt, capacity) * price
            }
            reliabilitySum += charger.reliabilityScore
            stops.add(
                ChargingStop(
                    chargerId = charger.id, name = charger.name,
                    latitude = charger.latitude, longitude = charger.longitude,
                    network = charger.network,
                    connectorTypes = charger.connectorTypes,
                    maxKw = charger.maxKw,
                    numberOfStalls = charger.numberOfStalls,
                    availableStalls = charger.availableStalls,
                    status = charger.status,
                    reliabilityScore = charger.reliabilityScore,
                    pricePerKwh = charger.pricePerKwh,
                    priceCurrencyCode = charger.priceCurrencyCode,
                    usageCostText = charger.usageCostText,
                    detourMinutes = charger.detourMinutes,
                    region = charger.region,
                    dataSource = charger.dataSource,
                    arrivalBatteryPercent = arrivalInt, targetBatteryPercent = targetInt,
                    chargeDurationMinutes = minutes,
                    dataProviderTitle = charger.dataProviderTitle,
                    dataProviderLicense = charger.dataProviderLicense,
                    dataProviderWebsiteUrl = charger.dataProviderWebsiteUrl,
                ),
            )
            soc = targetInt.toDouble()
        }

        // Guard on the raw (unrounded) arrival, matching iOS — rounding first would let a trip that
        // actually lands just below the reserve round up and pass.
        val finalArrivalRaw = soc - legDistancesKm.last() * socPerKm
        if (finalArrivalRaw < arrivalBufferPercent) return null
        val finalArrival = finalArrivalRaw.roundToInt().coerceIn(0, 100)
        val drivingMinutes = legDurationMinutes.sum()
        val detourMinutes = maxOf(0, drivingMinutes - directMinutes)
        val averageReliability = if (sequence.isNotEmpty()) reliabilitySum / sequence.size else 100.0

        return RouteOption(
            id = id,
            objective = RouteObjective.VERIFIED,
            supportedObjectives = emptySet(),
            title = "Candidate route",
            mode = "Candidate",
            totalEtaMinutes = drivingMinutes + chargingMinutes,
            drivingMinutes = drivingMinutes,
            chargingMinutes = chargingMinutes,
            detourMinutes = detourMinutes,
            arrivalBatteryPercent = finalArrival,
            riskScore = maxOf(1.0, (100 - averageReliability).roundToInt().toDouble()),
            chargingStops = stops,
            itinerary = itinerary,
            geometry = legGeometries.flatten(),
            estimatedChargingCostValue = if (sequence.isNotEmpty() && hasCompleteCost) totalCost else null,
            estimatedChargingCostCurrencyCode = if (sequence.isNotEmpty() && hasCompleteCost) costCurrency else null,
            estimatedCostText = if (sequence.isNotEmpty() && hasCompleteCost && costCurrency != null) {
                "${currencySymbol(costCurrency)}${"%.2f".format(java.util.Locale.US, totalCost)}"
            } else null,
            estimatedConsumptionKwhPer100Km = vehicle.efficiencyKwhPerKm * 100.0,
            stopSegmentIndices = sequence.indices.toList(),
            routeSteps = indexedSteps(legSteps),
        )
    }

    /**
     * Builds one multi-stop route that drives THROUGH the driver's own waypoints, inserting charging
     * only where the whole trip needs it. [vias] is the full travel-ordered list of intermediate
     * points (user waypoints and chargers interleaved by position); [legDistancesKm] /
     * [legDurationMinutes] describe start→via0, via0→via1, …, viaN→destination (size = vias.size + 1).
     * User vias are driven through with no charge; charger vias top up enough to reach the next
     * charger (10% reserve) or the destination (arrival-buffer reserve). Returns null if any leg is
     * energy-infeasible. Faithful port of iOS `buildRouteThroughWaypoints`.
     */
    fun buildRouteThroughWaypoints(
        id: String,
        vias: List<PlannedVia>,
        userWaypoints: List<PlaceCandidate>,
        legDistancesKm: List<Double>,
        legDurationMinutes: List<Int>,
        directMinutes: Int,
        vehicle: Vehicle,
        currentSOC: Double,
        arrivalBufferPercent: Double,
        legGeometries: List<List<LatLon>> = emptyList(),
        legSteps: List<List<DrivingStep>> = emptyList(),
    ): RouteOption? {
        if (legDistancesKm.size != vias.size + 1 || legDurationMinutes.size != vias.size + 1) return null
        val capacity = maxOf(1.0, vehicle.batteryCapacityKwh)
        val socPerKm = vehicle.efficiencyKwhPerKm / capacity * 100

        var soc = currentSOC
        val stops = mutableListOf<ChargingStop>()
        val stopSegmentIndices = mutableListOf<Int>()
        val userWaypointSegmentIndices = IntArray(userWaypoints.size) { -1 }
        val itinerary = mutableListOf<ItineraryStop>()
        var elapsedMinutes = 0
        var chargingMinutes = 0
        var totalCost = 0.0
        var costCurrency: String? = null
        var hasCompleteCost = true
        var reliabilitySum = 0.0
        var chargerCount = 0

        for (i in vias.indices) {
            val arrival = soc - legDistancesKm[i] * socPerKm
            if (arrival < 5) return null
            val via = vias[i]
            via.userWaypointIndex?.let { if (it in userWaypointSegmentIndices.indices) userWaypointSegmentIndices[it] = i }

            val charger = via.charger
            if (charger == null) {
                // User stop: drive through, record the visit, no charge.
                elapsedMinutes += legDurationMinutes[i]
                itinerary.add(
                    ItineraryStop(via.name, ItineraryStop.Kind.VISIT, elapsedMinutes, arrival.roundToInt().coerceIn(0, 100)),
                )
                soc = arrival
                continue
            }

            // Distance from here to the NEXT charger (spanning any user stops between), else destination.
            var requiredKm = 0.0
            var reachedNextCharger = false
            var k = i + 1
            while (k <= vias.size) {
                requiredKm += legDistancesKm[k]
                if (k < vias.size && vias[k].charger != null) { reachedNextCharger = true; break }
                k++
            }
            val reserve = if (reachedNextCharger) 10.0 else arrivalBufferPercent
            val requiredDeparture = requiredKm * socPerKm + reserve
            if (requiredDeparture > 95) return null
            val target = minOf(95.0, maxOf(arrival, requiredDeparture))
            val arrivalInt = arrival.roundToInt().coerceIn(0, 100)
            val targetInt = maxOf(arrivalInt, minOf(95, ceil(target).toInt()))
            if (targetInt <= arrivalInt) return null
            val compatibleKw = charger.compatiblePower(vehicle.routingConnectorTypes) ?: return null
            val effectiveKw = maxOf(1.0, minOf(compatibleKw.toDouble(), vehicle.maxDcChargingKw.toDouble()))
            val minutes = ChargePlanner.chargeMinutes(arrivalInt, targetInt, capacity, effectiveKw)

            elapsedMinutes += legDurationMinutes[i]
            itinerary.add(ItineraryStop(charger.name, ItineraryStop.Kind.CHARGING, elapsedMinutes, arrivalInt))
            elapsedMinutes += minutes
            chargingMinutes += minutes
            val price = charger.pricePerKwh
            val currency = charger.priceCurrencyCode
            if (price == null || currency == null || (costCurrency != null && costCurrency != currency)) {
                hasCompleteCost = false
            } else {
                costCurrency = currency
                totalCost += ChargePlanner.energyAdded(arrivalInt, targetInt, capacity) * price
            }
            reliabilitySum += charger.reliabilityScore
            chargerCount++
            stops.add(
                ChargingStop(
                    chargerId = charger.id, name = charger.name,
                    latitude = charger.latitude, longitude = charger.longitude,
                    network = charger.network,
                    connectorTypes = charger.connectorTypes,
                    maxKw = charger.maxKw,
                    numberOfStalls = charger.numberOfStalls,
                    availableStalls = charger.availableStalls,
                    status = charger.status,
                    reliabilityScore = charger.reliabilityScore,
                    pricePerKwh = charger.pricePerKwh,
                    priceCurrencyCode = charger.priceCurrencyCode,
                    usageCostText = charger.usageCostText,
                    detourMinutes = charger.detourMinutes,
                    region = charger.region,
                    dataSource = charger.dataSource,
                    arrivalBatteryPercent = arrivalInt, targetBatteryPercent = targetInt,
                    chargeDurationMinutes = minutes,
                    dataProviderTitle = charger.dataProviderTitle,
                    dataProviderLicense = charger.dataProviderLicense,
                    dataProviderWebsiteUrl = charger.dataProviderWebsiteUrl,
                ),
            )
            stopSegmentIndices.add(i)
            soc = targetInt.toDouble()
        }

        // Guard on the raw (unrounded) arrival, matching iOS — rounding first would let a trip that
        // actually lands just below the reserve round up and pass.
        val finalArrivalRaw = soc - legDistancesKm.last() * socPerKm
        if (finalArrivalRaw < arrivalBufferPercent) return null
        val finalArrival = finalArrivalRaw.roundToInt().coerceIn(0, 100)
        val drivingMinutes = legDurationMinutes.sum()
        val detourMinutes = maxOf(0, drivingMinutes - directMinutes)
        val averageReliability = if (chargerCount > 0) reliabilitySum / chargerCount else 100.0

        return RouteOption(
            id = id,
            objective = RouteObjective.VERIFIED,
            supportedObjectives = emptySet(),
            title = "Candidate route",
            mode = "Candidate",
            totalEtaMinutes = drivingMinutes + chargingMinutes,
            drivingMinutes = drivingMinutes,
            chargingMinutes = chargingMinutes,
            detourMinutes = detourMinutes,
            arrivalBatteryPercent = finalArrival,
            riskScore = maxOf(1.0, (100 - averageReliability).roundToInt().toDouble()),
            chargingStops = stops,
            itinerary = itinerary,
            geometry = legGeometries.flatten(),
            estimatedChargingCostValue = if (chargerCount > 0 && hasCompleteCost) totalCost else null,
            estimatedChargingCostCurrencyCode = if (chargerCount > 0 && hasCompleteCost) costCurrency else null,
            estimatedCostText = if (chargerCount > 0 && hasCompleteCost && costCurrency != null) {
                "${currencySymbol(costCurrency)}${"%.2f".format(java.util.Locale.US, totalCost)}"
            } else null,
            estimatedConsumptionKwhPer100Km = vehicle.efficiencyKwhPerKm * 100.0,
            stopSegmentIndices = stopSegmentIndices,
            userWaypoints = userWaypoints,
            userWaypointSegmentIndices = userWaypointSegmentIndices.toList(),
            routeSteps = indexedSteps(legSteps),
        )
    }

    /** Picks the best candidate per user objective and merges options that share a charger sequence. */
    fun optimize(candidates: List<RouteOption>): List<RouteOption> {
        val selected = mutableListOf<RouteOption>()
        val comparableCostCurrency = comparableCostCurrency(candidates)
        for (objective in RouteObjective.plannerCases) {
            if (objective == RouteObjective.LOWEST_COST && comparableCostCurrency == null) {
                continue
            }
            val best = candidates.minWithOrNull(comparator(objective)) ?: continue
            selected.add(labeled(best, objective))
        }
        return deduplicatedOptions(selected)
    }

    /**
     * A numeric price is meaningful only beside its ISO currency. Do not rank USD, CAD, EUR, etc.
     * against one another without a live exchange-rate source, and do not trust legacy/incomplete
     * candidates that carry a number without a currency code.
     */
    private fun comparableCostCurrency(candidates: List<RouteOption>): String? {
        val priced = candidates.filter { it.estimatedChargingCostValue != null }
        if (priced.isEmpty() || priced.any { it.estimatedChargingCostCurrencyCode.isNullOrBlank() }) return null
        val currencies = priced.map { it.estimatedChargingCostCurrencyCode!!.uppercase() }.toSet()
        return currencies.singleOrNull()
    }

    fun deduplicatedOptions(options: List<RouteOption>): List<RouteOption> {
        val unique = mutableListOf<RouteOption>()
        val indexByKey = mutableMapOf<String, Int>()
        for (option in options) {
            val key = sequenceKey(option)
            val existingIndex = indexByKey[key]
            if (existingIndex != null) {
                val existing = unique[existingIndex]
                unique[existingIndex] = existing.copy(
                    supportedObjectives = existing.supportedObjectives + option.supportedObjectives,
                )
            } else {
                indexByKey[key] = unique.size
                unique.add(option)
            }
        }
        return unique
    }

    private fun labeled(option: RouteOption, objective: RouteObjective): RouteOption =
        option.copy(
            title = objective.title,
            mode = objective.mode,
            objective = objective,
            supportedObjectives = setOf(objective),
        )

    private fun sequenceKey(option: RouteOption): String =
        ChargerScoring.sequenceKey(option.chargingStops.map { it.chargerId })

    private fun currencySymbol(code: String): String = when (code) {
        "USD", "CAD", "AUD", "NZD" -> "$"
        "EUR" -> "€"
        "GBP" -> "£"
        "NOK", "SEK" -> "kr"
        "CHF" -> "CHF"
        else -> "$code "
    }

    private fun indexedSteps(legs: List<List<DrivingStep>>): List<DrivingStep> =
        legs.flatMapIndexed { segmentIndex, steps ->
            steps.map { it.copy(segmentIndex = segmentIndex) }
        }

    private fun comparator(objective: RouteObjective): Comparator<RouteOption> {
        fun tieBreak(a: RouteOption, b: RouteOption): Int {
            if (a.totalEtaMinutes != b.totalEtaMinutes) return a.totalEtaMinutes.compareTo(b.totalEtaMinutes)
            if (a.chargingStops.size != b.chargingStops.size) return a.chargingStops.size.compareTo(b.chargingStops.size)
            if (a.riskScore != b.riskScore) return a.riskScore.compareTo(b.riskScore)
            return sequenceKey(a).compareTo(sequenceKey(b))
        }
        return Comparator { a, b ->
            when (objective) {
                RouteObjective.FASTEST, RouteObjective.DIRECT, RouteObjective.VERIFIED -> tieBreak(a, b)
                RouteObjective.FEWEST_STOPS ->
                    if (a.chargingStops.size != b.chargingStops.size) {
                        a.chargingStops.size.compareTo(b.chargingStops.size)
                    } else {
                        tieBreak(a, b)
                    }
                RouteObjective.RELIABLE ->
                    if (a.riskScore != b.riskScore) a.riskScore.compareTo(b.riskScore) else tieBreak(a, b)
                RouteObjective.LOWEST_COST -> {
                    val ac = a.estimatedChargingCostValue ?: Double.MAX_VALUE
                    val bc = b.estimatedChargingCostValue ?: Double.MAX_VALUE
                    if (ac != bc) ac.compareTo(bc) else tieBreak(a, b)
                }
            }
        }
    }
}
