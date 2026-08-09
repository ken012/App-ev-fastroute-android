package com.evfastroute.core

// Data models the pure planner logic operates on — minimal, faithful ports of the iOS
// Charger / Vehicle / ConnectorType surface (only the fields the routing/charging intelligence
// uses). Richer UI-facing fields (make/model/catalog, live status text, …) live in the :app layer.

enum class ConnectorType { CCS, CCS2, CHADEMO, NACS, TYPE2, J1772, OTHER }

enum class ChargerStatus { AVAILABLE, BUSY, LIMITED, OFFLINE }

enum class ChargerDataSource { OPEN_CHARGE_MAP, SAMPLE }

data class ChargerConnector(val type: ConnectorType, val maxKw: Int)

data class Charger(
    val id: String,
    val name: String,
    val network: String,
    val latitude: Double,
    val longitude: Double,
    val connectorTypes: List<ConnectorType>,
    val maxKw: Int,
    val numberOfStalls: Int,
    val availableStalls: Int? = null,
    val status: ChargerStatus = ChargerStatus.AVAILABLE,
    val reliabilityScore: Double = 90.0,
    val pricePerKwh: Double = 0.0,
    val detourMinutes: Int = 0,
    val region: String = "",
    val dataSource: ChargerDataSource = ChargerDataSource.OPEN_CHARGE_MAP,
    val connectorDetails: List<ChargerConnector> = emptyList(),
) {
    /** Best kW usable by a vehicle's connectors, or null if incompatible. Port of iOS compatiblePower. */
    fun compatiblePower(vehicleConnectors: List<ConnectorType>): Int? {
        val vehicleTypes = vehicleConnectors.toSet()
        val detailed = connectorDetails.filter { it.type in vehicleTypes }.maxOfOrNull { it.maxKw }
        if (detailed != null) return detailed
        return if (connectorTypes.toSet().intersect(vehicleTypes).isEmpty()) null else maxKw
    }

    /** Free-stall ratio, or null when live availability is unknown. Mirrors iOS availabilityRatio. */
    val availabilityRatio: Double?
        get() = availableStalls?.let { free -> if (numberOfStalls > 0) free.toDouble() / numberOfStalls else null }
}

data class Vehicle(
    val batteryCapacityKwh: Double,
    val efficiencyKwhPerKm: Double,
    val maxDcChargingKw: Int,
    val connectorTypes: List<ConnectorType>,
    val batteryHealthPercent: Double? = null,
)

/** A charger placed on the route: distance travelled along it and how far off-corridor. */
data class ProjectedCharger(
    val charger: Charger,
    val progressKm: Double,
    val corridorKm: Double,
)
