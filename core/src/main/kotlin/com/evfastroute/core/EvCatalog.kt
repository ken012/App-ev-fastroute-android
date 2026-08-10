package com.evfastroute.core

import java.text.Normalizer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Selectable EV specifications. Faithful port of the iOS EVPreset / EVCatalog built-in set: the
// fallback presets plus the Kia manufacturer supplements. Android loads the same bundled OpenEV
// JSON as iOS at startup and falls back to this built-in set if validation fails.

/**
 * A selectable vehicle specification. Catalog values are deliberately *starting* values: every
 * field stays editable in the garage because trims, wheels and market variants differ from a
 * published specification.
 */
data class EvPreset(
    val make: String,
    val model: String,
    val year: Int,
    val batteryCapacityKwh: Double,
    val maxDcChargingKw: Int,
    val efficiencyKwhPerKm: Double,
    val connectorTypes: List<ConnectorType>,
    val catalogIdentifier: String = defaultIdentifier(make, model, year),
    val ratedRangeKm: Double? = null,
    val rangeStandard: String? = null,
    val sourceName: String? = null,
    val sourceUrl: String? = null,
) {
    /** "2025 Tesla Model 3 Long Range" — what the picker and the vehicle card show. */
    val displayName: String get() = "$year $make $model"

    /**
     * Converts the global catalog connectors into the standards used by the selected trip region.
     * European markets use CCS2 (and Teslas there use CCS2, not NACS); elsewhere CCS2 collapses to
     * CCS. Mirrors iOS `connectorTypes(for:)`. The list can still be corrected by the user.
     */
    fun connectorTypes(european: Boolean): List<ConnectorType> {
        val mapped = connectorTypes.map { connector ->
            if (european) {
                when (connector) {
                    ConnectorType.CCS, ConnectorType.NACS -> ConnectorType.CCS2
                    else -> connector
                }
            } else if (connector == ConnectorType.CCS2) {
                ConnectorType.CCS
            } else {
                connector
            }
        }
        return mapped.distinct() // preserve order, drop duplicates
    }

    /** The physics-only [Vehicle] the planner consumes. */
    fun toVehicle(european: Boolean = false): Vehicle = Vehicle(
        batteryCapacityKwh = batteryCapacityKwh,
        efficiencyKwhPerKm = efficiencyKwhPerKm,
        maxDcChargingKw = maxDcChargingKw,
        connectorTypes = connectorTypes(european),
    )

    companion object {
        fun defaultIdentifier(make: String, model: String, year: Int): String =
            "built-in:$make:$model:$year".lowercase().replace(" ", "-")
    }
}

object EvCatalog {

    /** Built-in starter set (fallback presets + manufacturer supplements), always available even if
     * the bundled 789-car catalog can't be read. `lazy` so the lists below initialize first. */
    private val builtIn: List<EvPreset> by lazy { deduplicatedAndSorted(fallbackPresets + manufacturerSupplements) }

    /** The bundled OpenEV catalog once loaded at startup; null until then (or if loading failed). */
    @Volatile private var loaded: List<EvPreset>? = null

    private val catalogJson = Json { ignoreUnknownKeys = true }

    /** Active catalog: the full bundled set when loaded, else the built-in starter set. */
    val presets: List<EvPreset> get() = loaded ?: builtIn

    /** Distinct manufacturer count — used by the picker header ("N makes"). */
    val makeCount: Int get() = presets.map { it.make }.toSet().size

    /** Sensible starting car when the user hasn't chosen one yet. */
    val default: EvPreset get() = presets.firstOrNull { it.make == "Tesla" && it.model.startsWith("Model 3") }
        ?: presets.firstOrNull { it.make == "Tesla" }
        ?: fallbackPresets.first()

    /**
     * Token-AND search over "year make model", diacritic/case/punctuation-insensitive, also matching
     * against a whitespace-stripped haystack so "modely" finds "Model Y". Mirrors iOS EVCatalog.search.
     */
    // Cache the (expensive) normalized "year make model" haystack per preset so a keystroke
    // normalizes only the query, not all ~789 catalog entries. Keyed by the unique catalogIdentifier.
    private val haystackByIdentifier = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun searchHaystack(preset: EvPreset): String =
        haystackByIdentifier.getOrPut(preset.catalogIdentifier) {
            normalize("${preset.year} ${preset.make} ${preset.model}")
        }

    fun search(query: String): List<EvPreset> {
        val terms = normalize(query).split(' ').filter { it.isNotBlank() }
        if (terms.isEmpty()) return presets
        return presets.filter { preset ->
            val haystack = searchHaystack(preset)
            val compact = haystack.replace(" ", "")
            terms.all { term -> haystack.contains(term) || compact.contains(term) }
        }
    }

    fun preset(withIdentifier: String?): EvPreset? =
        withIdentifier?.let { id -> presets.firstOrNull { it.catalogIdentifier == id } }

    /**
     * Parses a bundled OpenEV catalog document and, when it looks complete, promotes it to the
     * active catalog (merged with the manufacturer supplements). No-ops on a malformed or
     * suspiciously small document so the built-in starter set stays. Returns true if accepted.
     * Called once at app startup (`:app` reads the `ev_catalog.json` asset).
     */
    fun loadBundledCatalog(json: String, minimumVehicles: Int = 700): Boolean {
        val parsed = parseDocument(json) ?: return false
        if (parsed.size < minimumVehicles) return false
        loaded = deduplicatedAndSorted(parsed + manufacturerSupplements)
        return true
    }

    /** Decodes the document and maps its vehicles to presets. Null on decode failure or a schema
     * version this build doesn't understand. No size gate — [loadBundledCatalog] applies that. */
    fun parseDocument(json: String): List<EvPreset>? {
        val document = runCatching {
            catalogJson.decodeFromString(EvCatalogDocument.serializer(), json)
        }.getOrNull() ?: return null
        if (document.schemaVersion != 1) return null
        return document.vehicles.map { it.toPreset() }
    }

    /** Test hook: drop any loaded catalog and fall back to the built-in set. */
    internal fun resetForTest() { loaded = null }

    private fun normalize(text: String): String {
        val folded = Normalizer.normalize(text, Normalizer.Form.NFKD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
        return buildString {
            folded.forEach { ch -> append(if (ch.isLetterOrDigit()) ch else ' ') }
        }
    }

    private fun deduplicatedAndSorted(values: List<EvPreset>): List<EvPreset> {
        val seen = mutableSetOf<String>()
        return values
            .sortedWith(compareBy({ it.make.lowercase() }, { it.model.lowercase() }, { -it.year }))
            .filter { seen.add(it.catalogIdentifier) }
    }

    // Kia is absent from OpenEV v1.24.0; these come from Kia's 2024 US specifications and keep a
    // major North-American brand discoverable. (Ported verbatim from iOS.)
    private val manufacturerSupplements: List<EvPreset> = listOf(
        EvPreset(
            catalogIdentifier = "kia-media:ev6:2024:light-rwd",
            make = "Kia", model = "EV6 Light RWD", year = 2024,
            batteryCapacityKwh = 58.0, maxDcChargingKw = 180,
            efficiencyKwhPerKm = 58.0 / (232 * 1.609344), connectorTypes = listOf(ConnectorType.CCS),
            ratedRangeKm = 232 * 1.609344, rangeStandard = "EPA",
            sourceName = "Kia Media (manufacturer)",
            sourceUrl = "https://www.kiamedia.com/us/en/models/ev6/2024/specifications",
        ),
        EvPreset(
            catalogIdentifier = "kia-media:ev6:2024:light-long-range-rwd",
            make = "Kia", model = "EV6 Light Long Range RWD", year = 2024,
            batteryCapacityKwh = 77.4, maxDcChargingKw = 240,
            efficiencyKwhPerKm = 77.4 / (310 * 1.609344), connectorTypes = listOf(ConnectorType.CCS),
            ratedRangeKm = 310 * 1.609344, rangeStandard = "EPA",
            sourceName = "Kia Media (manufacturer)",
            sourceUrl = "https://www.kiamedia.com/us/en/models/ev6/2024/specifications",
        ),
        EvPreset(
            catalogIdentifier = "kia-media:ev9:2024:light-rwd",
            make = "Kia", model = "EV9 Light RWD", year = 2024,
            batteryCapacityKwh = 76.1, maxDcChargingKw = 235,
            efficiencyKwhPerKm = 76.1 / (230 * 1.609344), connectorTypes = listOf(ConnectorType.CCS),
            ratedRangeKm = 230 * 1.609344, rangeStandard = "EPA",
            sourceName = "Kia Media (manufacturer)",
            sourceUrl = "https://www.kiamedia.com/us/en/models/ev9/2024/specifications",
        ),
        EvPreset(
            catalogIdentifier = "kia-media:ev9:2024:light-long-range-rwd",
            make = "Kia", model = "EV9 Light Long Range RWD", year = 2024,
            batteryCapacityKwh = 99.8, maxDcChargingKw = 210,
            efficiencyKwhPerKm = 99.8 / (304 * 1.609344), connectorTypes = listOf(ConnectorType.CCS),
            ratedRangeKm = 304 * 1.609344, rangeStandard = "EPA",
            sourceName = "Kia Media (manufacturer)",
            sourceUrl = "https://www.kiamedia.com/us/en/models/ev9/2024/specifications",
        ),
        EvPreset(
            catalogIdentifier = "kia-media:ev9:2024:wind-awd",
            make = "Kia", model = "EV9 Wind AWD", year = 2024,
            batteryCapacityKwh = 99.8, maxDcChargingKw = 210,
            efficiencyKwhPerKm = 99.8 / (280 * 1.609344), connectorTypes = listOf(ConnectorType.CCS),
            ratedRangeKm = 280 * 1.609344, rangeStandard = "EPA",
            sourceName = "Kia Media (manufacturer)",
            sourceUrl = "https://www.kiamedia.com/us/en/models/ev9/2024/specifications",
        ),
    )

    // Built-in starter set (iOS fallbackPresets). Real, current specs for the most common EVs.
    private val fallbackPresets: List<EvPreset> = listOf(
        EvPreset(make = "Tesla", model = "Model 3 Long Range", year = 2025, batteryCapacityKwh = 75.0, maxDcChargingKw = 250, efficiencyKwhPerKm = 0.15, connectorTypes = listOf(ConnectorType.NACS)),
        EvPreset(make = "Tesla", model = "Model Y Long Range", year = 2025, batteryCapacityKwh = 75.0, maxDcChargingKw = 250, efficiencyKwhPerKm = 0.17, connectorTypes = listOf(ConnectorType.NACS)),
        EvPreset(make = "Tesla", model = "Model S", year = 2025, batteryCapacityKwh = 100.0, maxDcChargingKw = 250, efficiencyKwhPerKm = 0.18, connectorTypes = listOf(ConnectorType.NACS)),
        EvPreset(make = "Tesla", model = "Model X", year = 2025, batteryCapacityKwh = 100.0, maxDcChargingKw = 250, efficiencyKwhPerKm = 0.21, connectorTypes = listOf(ConnectorType.NACS)),
        EvPreset(make = "Hyundai", model = "IONIQ 5", year = 2025, batteryCapacityKwh = 77.4, maxDcChargingKw = 235, efficiencyKwhPerKm = 0.18, connectorTypes = listOf(ConnectorType.CCS)),
        EvPreset(make = "Hyundai", model = "IONIQ 6", year = 2025, batteryCapacityKwh = 77.4, maxDcChargingKw = 235, efficiencyKwhPerKm = 0.15, connectorTypes = listOf(ConnectorType.CCS)),
        EvPreset(make = "Ford", model = "Mustang Mach-E", year = 2025, batteryCapacityKwh = 91.0, maxDcChargingKw = 150, efficiencyKwhPerKm = 0.21, connectorTypes = listOf(ConnectorType.CCS, ConnectorType.NACS)),
        EvPreset(make = "Ford", model = "F-150 Lightning", year = 2025, batteryCapacityKwh = 131.0, maxDcChargingKw = 155, efficiencyKwhPerKm = 0.33, connectorTypes = listOf(ConnectorType.CCS, ConnectorType.NACS)),
        EvPreset(make = "Chevrolet", model = "Equinox EV", year = 2025, batteryCapacityKwh = 85.0, maxDcChargingKw = 150, efficiencyKwhPerKm = 0.19, connectorTypes = listOf(ConnectorType.CCS)),
        EvPreset(make = "Rivian", model = "R1T", year = 2025, batteryCapacityKwh = 135.0, maxDcChargingKw = 220, efficiencyKwhPerKm = 0.30, connectorTypes = listOf(ConnectorType.CCS, ConnectorType.NACS)),
        EvPreset(make = "Rivian", model = "R1S", year = 2025, batteryCapacityKwh = 135.0, maxDcChargingKw = 220, efficiencyKwhPerKm = 0.31, connectorTypes = listOf(ConnectorType.CCS, ConnectorType.NACS)),
        EvPreset(make = "Volkswagen", model = "ID.4", year = 2025, batteryCapacityKwh = 82.0, maxDcChargingKw = 175, efficiencyKwhPerKm = 0.20, connectorTypes = listOf(ConnectorType.CCS)),
        EvPreset(make = "BMW", model = "i4 eDrive40", year = 2025, batteryCapacityKwh = 81.0, maxDcChargingKw = 205, efficiencyKwhPerKm = 0.17, connectorTypes = listOf(ConnectorType.CCS2)),
        EvPreset(make = "BMW", model = "iX xDrive50", year = 2025, batteryCapacityKwh = 105.0, maxDcChargingKw = 195, efficiencyKwhPerKm = 0.22, connectorTypes = listOf(ConnectorType.CCS2)),
        EvPreset(make = "Mercedes-Benz", model = "EQE", year = 2025, batteryCapacityKwh = 90.0, maxDcChargingKw = 170, efficiencyKwhPerKm = 0.18, connectorTypes = listOf(ConnectorType.CCS2)),
        EvPreset(make = "Polestar", model = "2", year = 2025, batteryCapacityKwh = 82.0, maxDcChargingKw = 205, efficiencyKwhPerKm = 0.18, connectorTypes = listOf(ConnectorType.CCS2)),
        EvPreset(make = "Nissan", model = "Ariya", year = 2025, batteryCapacityKwh = 87.0, maxDcChargingKw = 130, efficiencyKwhPerKm = 0.19, connectorTypes = listOf(ConnectorType.CCS)),
        EvPreset(make = "Audi", model = "Q4 e-tron", year = 2025, batteryCapacityKwh = 82.0, maxDcChargingKw = 175, efficiencyKwhPerKm = 0.20, connectorTypes = listOf(ConnectorType.CCS2)),
    )
}

/** Maps the OpenEV catalog's connector tokens (CCS1/CCS2/NACS Tesla/CHAdeMO) to [ConnectorType]. */
private fun catalogConnector(raw: String): ConnectorType? = when (raw.uppercase()) {
    "CCS", "CCS1" -> ConnectorType.CCS
    "CCS2" -> ConnectorType.CCS2
    "CHADEMO" -> ConnectorType.CHADEMO
    "NACS", "TESLA", "NACS/TESLA" -> ConnectorType.NACS
    "TYPE2", "TYPE 2" -> ConnectorType.TYPE2
    else -> null
}

@Serializable
private data class EvCatalogDocument(
    val schemaVersion: Int = 0,
    val vehicles: List<CatalogVehicle> = emptyList(),
)

@Serializable
private data class CatalogVehicle(
    val catalogIdentifier: String? = null,
    val make: String,
    val model: String,
    val year: Int,
    val batteryCapacityKwh: Double,
    val maxDcChargingKw: Int,
    val efficiencyKwhPerKm: Double,
    val connectorTypes: List<String> = emptyList(),
    val ratedRangeKm: Double? = null,
    val rangeStandard: String? = null,
    val sourceName: String? = null,
    val sourceURL: String? = null,
) {
    fun toPreset(): EvPreset = EvPreset(
        make = make,
        model = model,
        year = year,
        batteryCapacityKwh = batteryCapacityKwh,
        maxDcChargingKw = maxDcChargingKw,
        efficiencyKwhPerKm = efficiencyKwhPerKm,
        connectorTypes = connectorTypes.mapNotNull(::catalogConnector),
        catalogIdentifier = catalogIdentifier ?: EvPreset.defaultIdentifier(make, model, year),
        ratedRangeKm = ratedRangeKm,
        rangeStandard = rangeStandard,
        sourceName = sourceName,
        sourceUrl = sourceURL,
    )
}
