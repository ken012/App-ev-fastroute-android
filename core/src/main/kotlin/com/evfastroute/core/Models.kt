package com.evfastroute.core

// Data models the pure planner logic operates on — minimal, faithful ports of the iOS
// Charger / Vehicle / ConnectorType surface (only the fields the routing/charging intelligence
// uses). Richer UI-facing fields (make/model/catalog, live status text, …) live in the :app layer.

// Keep this set identical to iOS `ConnectorType`. These are the connector standards the
// road-trip planner can verify against Open Charge Map; unsupported/unknown catalog tokens are
// rejected rather than becoming an Android-only compatibility choice.
enum class ConnectorType { CCS, CCS2, NACS, CHADEMO, TYPE2, J1772 }

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
    val status: ChargerStatus = ChargerStatus.LIMITED,
    /** Data-confidence score derived only from fields OCM actually reports; never live uptime. */
    val reliabilityScore: Double = 50.0,
    /** Null means OCM did not publish an unambiguous per-kWh rate. Unknown is never treated as free. */
    val pricePerKwh: Double? = null,
    val priceCurrencyCode: String? = null,
    /** Provider's human-readable usage-cost text, retained for station details. */
    val usageCostText: String? = null,
    val detourMinutes: Int = 0,
    val region: String = "",
    val dataSource: ChargerDataSource = ChargerDataSource.OPEN_CHARGE_MAP,
    val connectorDetails: List<ChargerConnector> = emptyList(),
    /** Provider-specific attribution returned by OCM. Kept with every routed stop because OCM
     * requires the applicable provider and license to remain visible to end users. */
    val dataProviderTitle: String? = null,
    val dataProviderLicense: String? = null,
    val dataProviderWebsiteUrl: String? = null,
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
    /**
     * Explicit confirmation that this NACS vehicle supports CCS1 and the driver carries the
     * required adapter. Null means an older profile has not answered the question yet.
     */
    val ccs1AdapterAvailable: Boolean? = null,
) {
    val offersCcs1AdapterOption: Boolean
        get() = ConnectorType.NACS in connectorTypes

    /** Older profiles may contain CCS from a previous automatic expansion. Never route through
     * those stations until the driver explicitly confirms the hardware and adapter. */
    val requiresCcs1AdapterConfirmation: Boolean
        get() = offersCcs1AdapterOption &&
            ConnectorType.CCS in connectorTypes &&
            ccs1AdapterAvailable == null

    /** Connector capabilities that are safe to use for route planning. */
    val routingConnectorTypes: List<ConnectorType>
        get() {
            if (!offersCcs1AdapterOption) return connectorTypes.distinct()
            val safe = connectorTypes.toMutableSet()
            if (ccs1AdapterAvailable == true) {
                safe += ConnectorType.CCS
            } else {
                safe -= ConnectorType.CCS
            }
            return ConnectorType.entries.filter { it in safe }
        }
}

/** A charger placed on the route: distance travelled along it and how far off-corridor. */
data class ProjectedCharger(
    val charger: Charger,
    val progressKm: Double,
    val corridorKm: Double,
)
