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
    val estimatedCostText: String? = null,
)
