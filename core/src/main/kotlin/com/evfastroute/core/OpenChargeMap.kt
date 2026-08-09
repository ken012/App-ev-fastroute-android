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
private data class OcmDataProvider(
    val Title: String? = null,
    val WebsiteURL: String? = null,
    val License: String? = null,
    val IsOpenDataLicensed: Boolean? = null,
)

@Serializable
private data class OcmPoi(
    val ID: Long? = null,
    val AddressInfo: OcmAddressInfo? = null,
    val OperatorInfo: OcmOperatorInfo? = null,
    val StatusType: OcmStatusType? = null,
    val Connections: List<OcmConnection>? = null,
    val NumberOfPoints: Int? = null,
    val UsageCost: String? = null,
    val DataProvider: OcmDataProvider? = null,
)

object OpenChargeMap {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Null means the service payload was malformed; an empty list means a valid response with no
     * usable POIs. Keeping those outcomes separate prevents a provider outage masquerading as
     * "there are no stations here." */
    fun parseOrNull(responseBody: String): List<Charger>? =
        runCatching { json.decodeFromString<List<OcmPoi>>(responseBody).mapNotNull { charger(it) } }
            .getOrNull()

    /** Back-compatible convenience for pure callers that only need a list. */
    fun parse(responseBody: String): List<Charger> = parseOrNull(responseBody) ?: emptyList()

    private fun charger(poi: OcmPoi): Charger? {
        val address = poi.AddressInfo ?: return null
        val lat = address.Latitude ?: return null
        val lon = address.Longitude ?: return null
        if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        val id = poi.ID ?: return null

        // Keep connector-specific power rather than flattening to the fastest (maybe incompatible) plug.
        val recognised = (poi.Connections ?: emptyList()).mapNotNull { connection ->
            val type = connectorFromTitle(connection.ConnectionType?.Title) ?: return@mapNotNull null
            val power = connection.PowerKW ?: return@mapNotNull null
            if (!power.isFinite() || power <= 0.0) return@mapNotNull null
            ChargerConnector(type = type, maxKw = power.toInt().coerceAtLeast(1))
        }
        if (recognised.isEmpty()) return null

        val connectors = recognised.map { it.type }.toSet().sortedBy { it.name }
        val maxKw = recognised.maxOf { it.maxKw }
        val quantitySum = (poi.Connections ?: emptyList()).sumOf { it.Quantity ?: 0 }
        val numberOfStalls = poi.NumberOfPoints
            ?: if (quantitySum > 0) quantitySum else maxOf(1, (poi.Connections ?: emptyList()).size)

        val isOperational = poi.StatusType?.IsOperational
        val status = when (isOperational) {
            true -> ChargerStatus.AVAILABLE
            false -> ChargerStatus.OFFLINE
            null -> ChargerStatus.LIMITED
        }
        val region = address.Country?.ISOCode?.uppercase() ?: "US"
        val parsedPrice = parsePricePerKwh(poi.UsageCost, region)
        val operator = poi.OperatorInfo?.Title?.trim().takeUnless { it.isNullOrEmpty() }
        val provider = poi.DataProvider

        return Charger(
            id = "ocm-$id",
            name = address.Title ?: "Charging station",
            network = operator ?: "Unknown network",
            latitude = lat,
            longitude = lon,
            connectorTypes = connectors,
            maxKw = maxKw,
            numberOfStalls = numberOfStalls,
            availableStalls = null, // OCM does not report live availability
            status = status,
            reliabilityScore = reliabilityEstimate(isOperational, maxKw, numberOfStalls, operator != null),
            pricePerKwh = parsedPrice?.value,
            priceCurrencyCode = parsedPrice?.currencyCode,
            detourMinutes = 5, // refined against the route by the app layer
            region = region,
            dataSource = ChargerDataSource.OPEN_CHARGE_MAP,
            connectorDetails = recognised,
            dataProviderTitle = provider?.Title?.trim().takeUnless { it.isNullOrEmpty() },
            dataProviderLicense = provider?.License?.trim().takeUnless { it.isNullOrEmpty() },
            dataProviderWebsiteUrl = provider?.WebsiteURL?.trim()?.takeIf(::isHttpsUrl),
        )
    }

    /** OCM has no live uptime metric. This score expresses completeness/confidence, not reliability. */
    fun reliabilityEstimate(
        isOperational: Boolean?,
        maxKw: Int,
        numberOfStalls: Int = 1,
        hasKnownOperator: Boolean = false,
    ): Double {
        var score = 45.0
        score += when (isOperational) {
            true -> 25.0
            false -> 0.0
            null -> 5.0
        }
        if (maxKw >= 50) score += 5
        if (maxKw >= 150) score += 5
        if (numberOfStalls > 1) score += 5
        if (hasKnownOperator) score += 10
        return score.coerceIn(0.0, 100.0)
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

    data class ParsedPrice(val value: Double, val currencyCode: String)

    /**
     * Parses only unambiguous energy rates. Session/time fees and arbitrary first numbers are
     * deliberately ignored: an unknown price must never masquerade as free or drive the cheapest
     * route objective. `$` is resolved from the station country when possible.
     */
    fun parsePricePerKwh(usageCost: String?, region: String): ParsedPrice? {
        val text = usageCost?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val lower = text.lowercase()
        val currency = currencyCode(text, region) ?: return null
        if (Regex("""\bfree\b|no\s+charge""", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return ParsedPrice(0.0, currency)
        }
        val rateMatch = Regex(
            """(\d+(?:[.,]\d+)?)(?:\s*(?:CAD|USD|AUD|NZD|EUR|GBP|CHF|\$|€|£))?\s*(?:/\s*|per\s+)kwh\b""",
            RegexOption.IGNORE_CASE,
        ).find(lower) ?: return null
        val amount = rateMatch.groupValues[1]
            ?.replace(',', '.')
            ?.toDoubleOrNull()
            ?: return null
        if (!amount.isFinite() || amount < 0.0 || amount > 10.0) return null
        return ParsedPrice(amount, currency)
    }

    private fun currencyCode(text: String, region: String): String? {
        val upper = text.uppercase()
        return when {
            "CAD" in upper || "C\$" in upper -> "CAD"
            "USD" in upper || "US\$" in upper -> "USD"
            "EUR" in upper || '€' in text -> "EUR"
            "GBP" in upper || '£' in text -> "GBP"
            '$' in text -> when (region.uppercase()) {
                "CA" -> "CAD"
                "US" -> "USD"
                "AU" -> "AUD"
                "NZ" -> "NZD"
                else -> null
            }
            else -> when (region.uppercase()) {
                "CA" -> "CAD"
                "US" -> "USD"
                "AU" -> "AUD"
                "NZ" -> "NZD"
                "GB" -> "GBP"
                "AT", "BE", "CY", "DE", "EE", "ES", "FI", "FR", "GR", "HR", "IE", "IT",
                "LT", "LU", "LV", "MT", "NL", "PT", "SI", "SK" -> "EUR"
                else -> null
            }
        }
    }

    private fun isHttpsUrl(value: String): Boolean =
        runCatching { java.net.URI(value).let { it.scheme.equals("https", ignoreCase = true) && !it.host.isNullOrBlank() } }
            .getOrDefault(false)
}
