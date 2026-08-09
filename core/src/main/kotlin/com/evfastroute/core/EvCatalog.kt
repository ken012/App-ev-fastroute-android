package com.evfastroute.core

import java.text.Normalizer

// Selectable EV specifications. Faithful port of the iOS EVPreset / EVCatalog built-in set: the
// fallback presets plus the Kia manufacturer supplements. (iOS also bundles a 700-car OpenEV JSON
// and prefers it when present; wiring that asset in is a later add — the built-in set gives the
// picker real, correct behaviour today.)

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

    /** All built-in presets, de-duplicated by identifier and sorted make → model → newest year.
     * `lazy` so the preset lists (declared below) are initialized before this reads them. */
    val presets: List<EvPreset> by lazy { deduplicatedAndSorted(fallbackPresets + manufacturerSupplements) }

    /** Distinct manufacturer count — used by the picker header ("N makes"). */
    val makeCount: Int get() = presets.map { it.make }.toSet().size

    /** Sensible starting car when the user hasn't chosen one yet. */
    val default: EvPreset get() = presets.firstOrNull { it.make == "Tesla" && it.model.startsWith("Model 3") }
        ?: presets.first()

    /**
     * Token-AND search over "year make model", diacritic/case/punctuation-insensitive, also matching
     * against a whitespace-stripped haystack so "modely" finds "Model Y". Mirrors iOS EVCatalog.search.
     */
    fun search(query: String): List<EvPreset> {
        val terms = normalize(query).split(' ').filter { it.isNotBlank() }
        if (terms.isEmpty()) return presets
        return presets.filter { preset ->
            val haystack = normalize("${preset.year} ${preset.make} ${preset.model}")
            val compact = haystack.replace(" ", "")
            terms.all { term -> haystack.contains(term) || compact.contains(term) }
        }
    }

    fun preset(withIdentifier: String?): EvPreset? =
        withIdentifier?.let { id -> presets.firstOrNull { it.catalogIdentifier == id } }

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
