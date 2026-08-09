package com.evfastroute.core

// The planner's result models. Faithful (Android-shaped) ports of the iOS RouteOption /
// ChargingStop / ItineraryStop. `objective`/`supportedObjectives` keep the driver's intent as
// data so refreshes and reroutes survive UI de-duplication.

data class ChargingStop(
    val chargerId: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val arrivalBatteryPercent: Int,
    val targetBatteryPercent: Int,
    val chargeDurationMinutes: Int,
    val dataProviderTitle: String? = null,
    val dataProviderLicense: String? = null,
    val dataProviderWebsiteUrl: String? = null,
)

/** One arrival point in travel order (a charging stop or a user visit), with elapsed time + SOC. */
data class ItineraryStop(
    val name: String,
    val kind: Kind,
    val arrivalMinutesFromStart: Int,
    val arrivalBatteryPercent: Int,
) {
    enum class Kind { CHARGING, VISIT }
}

data class RouteOption(
    val id: String,
    val objective: RouteObjective,
    val supportedObjectives: Set<RouteObjective>,
    val title: String,
    val mode: String,
    val totalEtaMinutes: Int,
    val drivingMinutes: Int,
    val chargingMinutes: Int,
    val detourMinutes: Int,
    val arrivalBatteryPercent: Int,
    val riskScore: Double,
    val chargingStops: List<ChargingStop>,
    val itinerary: List<ItineraryStop>,
    /** The driven road path (start → chargers → destination) for the map polyline. */
    val geometry: List<LatLon> = emptyList(),
    val estimatedChargingCostValue: Double? = null,
    val estimatedChargingCostCurrencyCode: String? = null,
    val estimatedCostText: String? = null,
    /** Drive-segment index at whose end each charging stop is reached. Aligned to [chargingStops].
     * Because user waypoints add extra segments, these are the interleaved positions, not 0..n. */
    val stopSegmentIndices: List<Int> = emptyList(),
    /** The driver's own intermediate stops, in order (multi-stop trips); empty for a simple route. */
    val userWaypoints: List<PlaceCandidate> = emptyList(),
    /** Drive-segment index at whose end each user waypoint is reached. Aligned to [userWaypoints]. */
    val userWaypointSegmentIndices: List<Int> = emptyList(),
)
