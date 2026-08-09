package com.evfastroute.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Open Charge Map response parsing → Charger. Same API + mapping as the iOS OpenChargeMapService,
// so charger identity/power/connectors/reliability match across platforms. OCM is the one client
// shared with iOS; OpenRouteService/Photon are Android-only replacements for MapKit.

// --- Response DTOs (PascalCase to match the OCM JSON; unknown keys ignored) ---

@Serializable
private data class OcmConnectionType(val Title: String? = null)

@Serializable
private data class OcmConnection(
    val ConnectionType: OcmConnectionType? = null,
    val PowerKW: Double? = null,
    val Quantity: Int? = null,
)

@Serializable
private data class OcmCountry(val ISOCode: String? = null)

@Serializable
private data class OcmAddressInfo(
    val Title: String? = null,
    val Latitude: Double? = null,
    val Longitude: Double? = null,
    val Country: OcmCountry? = null,
)

@Serializable
private data class OcmOperatorInfo(val Title: String? = null)

@Serializable
private data class OcmStatusType(val IsOperational: Boolean? = null)

@Serializable
private data class OcmPoi(
    val ID: Long? = null,
    val AddressInfo: OcmAddressInfo? = null,
    val OperatorInfo: OcmOperatorInfo? = null,
    val StatusType: OcmStatusType? = null,
    val Connections: List<OcmConnection>? = null,
    val NumberOfPoints: Int? = null,
    val UsageCost: String? = null,
)

object OpenChargeMap {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(responseBody: String): List<Charger> =
        json.decodeFromString<List<OcmPoi>>(responseBody).mapNotNull { charger(it) }

    private fun charger(poi: OcmPoi): Charger? {
        val address = poi.AddressInfo ?: return null
        val lat = address.Latitude ?: return null
        val lon = address.Longitude ?: return null
        val id = poi.ID ?: return null

        // Keep connector-specific power rather than flattening to the fastest (maybe incompatible) plug.
        val recognised = (poi.Connections ?: emptyList()).mapNotNull { connection ->
            val type = connectorFromTitle(connection.ConnectionType?.Title) ?: return@mapNotNull null
            ChargerConnector(type = type, maxKw = maxOf(1, (connection.PowerKW ?: 50.0).toInt()))
        }
        if (recognised.isEmpty()) return null

        val connectors = recognised.map { it.type }.toSet().sortedBy { it.name }
        val maxKw = recognised.maxOf { it.maxKw }
        val quantitySum = (poi.Connections ?: emptyList()).sumOf { it.Quantity ?: 0 }
        val numberOfStalls = poi.NumberOfPoints
            ?: if (quantitySum > 0) quantitySum else maxOf(1, (poi.Connections ?: emptyList()).size)

        val isOperational = poi.StatusType?.IsOperational ?: true
        val status = if (isOperational) ChargerStatus.AVAILABLE else ChargerStatus.OFFLINE
        val region = address.Country?.ISOCode?.uppercase() ?: "US"

        return Charger(
            id = "ocm-$id",
            name = address.Title ?: "Charging station",
            network = poi.OperatorInfo?.Title ?: "Unknown network",
            latitude = lat,
            longitude = lon,
            connectorTypes = connectors,
            maxKw = maxKw,
            numberOfStalls = numberOfStalls,
            availableStalls = null, // OCM does not report live availability
            status = status,
            reliabilityScore = reliabilityEstimate(isOperational, maxKw),
            pricePerKwh = parsePricePerKwh(poi.UsageCost),
            detourMinutes = 5, // refined against the route by the app layer
            region = region,
            dataSource = ChargerDataSource.OPEN_CHARGE_MAP,
            connectorDetails = recognised,
        )
    }

    /** Reliability is not published by any free API — a transparent heuristic, shown as an estimate. */
    fun reliabilityEstimate(isOperational: Boolean, maxKw: Int): Double {
        var score = 78.0
        if (isOperational) score += 10
        if (maxKw >= 150) score += 6
        if (maxKw >= 250) score += 4
        return minOf(100.0, score)
    }

    /** Port of iOS ConnectorType.fromOpenChargeMap — order matters (specific combos before generic). */
    fun connectorFromTitle(title: String?): ConnectorType? {
        val raw = title?.lowercase() ?: return null
        if (raw.contains("ccs") || raw.contains("combo") || raw.contains("sae")) {
            return if (raw.contains("type 2") || raw.contains("combo 2") || raw.contains("type2")) {
                ConnectorType.CCS2
            } else {
                ConnectorType.CCS
            }
        }
        if (raw.contains("chademo")) return ConnectorType.CHADEMO
        if (raw.contains("tesla") || raw.contains("nacs")) return ConnectorType.NACS
        if (raw.contains("type 2") || raw.contains("type2") || raw.contains("mennekes")) return ConnectorType.TYPE2
        return null
    }

    /** Best-effort per-kWh price from OCM's free-text UsageCost (0 when unknown). */
    private fun parsePricePerKwh(usageCost: String?): Double {
        val text = usageCost ?: return 0.0
        val match = Regex("""\d+([.,]\d+)?""").find(text)?.value ?: return 0.0
        return match.replace(',', '.').toDoubleOrNull() ?: 0.0
    }
}
