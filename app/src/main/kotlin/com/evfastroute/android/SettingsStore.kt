package com.evfastroute.android

import android.content.Context
import androidx.core.content.edit
import com.evfastroute.core.ConnectorType
import com.evfastroute.core.EvCatalog
import com.evfastroute.core.EvPreset
import com.evfastroute.core.NavigationApp
import com.evfastroute.core.NavigationSession
import com.evfastroute.core.RangeDrivingStyle
import com.evfastroute.core.Region
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val MAX_USER_WAYPOINTS = 8
internal const val MAX_GARAGE_VEHICLES = 20
private const val MAX_PERSISTED_CLOCK_SKEW_MILLIS = 5 * 60_000L

internal fun normalizeGarageVehicleIdentifiers(values: List<String>): List<String> = values
    .map(String::trim)
    .filter { it.isNotEmpty() && it.length <= 200 }
    .distinct()
    .take(MAX_GARAGE_VEHICLES)

internal fun replacingGarageVehicleIdentifier(
    values: List<String>,
    original: String?,
    replacement: String,
): List<String> {
    val replaced = if (original != null && original in values) {
        values.map { if (it == original) replacement else it }
    } else {
        listOf(replacement) + values
    }
    return normalizeGarageVehicleIdentifiers(replaced)
}

/** Whether connector capabilities still match the catalog or were deliberately customized. */
@Serializable
enum class ConnectorConfigurationSource { CATALOG, USER }

@Serializable
data class VehicleOverride(
    val batteryCapacityKwh: Double,
    val maxDcChargingKw: Int,
    val efficiencyKwhPerKm: Double,
    val connectorNames: List<String>,
    val batteryHealthPercent: Double,
    val defaultArrivalBufferPercent: Int = 15,
    /** Null keeps profiles saved by older app versions decodable and safely unconfirmed. */
    val ccs1AdapterAvailable: Boolean? = null,
    val connectorConfigurationSource: ConnectorConfigurationSource? = null,
) {
    internal fun isValid(): Boolean =
        batteryCapacityKwh.isFinite() && batteryCapacityKwh in 15.0..300.0 &&
            maxDcChargingKw in 25..500 &&
            efficiencyKwhPerKm.isFinite() && efficiencyKwhPerKm > 0.05 && efficiencyKwhPerKm <= 0.50 &&
            batteryHealthPercent.isFinite() && batteryHealthPercent in 60.0..100.0 &&
            defaultArrivalBufferPercent in 5..35 &&
            connectorNames.isNotEmpty() && connectorNames.size <= ConnectorType.entries.size &&
            connectorNames.distinct().size == connectorNames.size &&
            connectorNames.all { name -> ConnectorType.entries.any { it.name == name } }

    fun applyTo(base: EvPreset): EvPreset = base.copy(
        batteryCapacityKwh = batteryCapacityKwh,
        maxDcChargingKw = maxDcChargingKw,
        efficiencyKwhPerKm = efficiencyKwhPerKm,
        connectorTypes = connectorNames.mapNotNull { name ->
            ConnectorType.entries.firstOrNull { it.name == name }
        }.ifEmpty { base.connectorTypes },
    )

    fun toVehicle(base: EvPreset): com.evfastroute.core.Vehicle {
        val configured = applyTo(base)
        return com.evfastroute.core.Vehicle(
            batteryCapacityKwh = configured.batteryCapacityKwh,
            efficiencyKwhPerKm = configured.efficiencyKwhPerKm,
            maxDcChargingKw = configured.maxDcChargingKw,
            connectorTypes = configured.connectorTypes,
            batteryHealthPercent = batteryHealthPercent,
            ccs1AdapterAvailable = ccs1AdapterAvailable,
        )
    }

    /** Nil provenance is inferred conservatively for profiles created before this field existed. */
    fun usesCatalogConnectorDefaults(base: EvPreset, european: Boolean): Boolean {
        if (connectorConfigurationSource == ConnectorConfigurationSource.USER) return false
        if (connectorConfigurationSource == ConnectorConfigurationSource.CATALOG) return true
        return toVehicle(base).routingConnectorTypes.toSet() == base.connectorTypes(european).toSet()
    }

    fun remappedToCatalog(base: EvPreset, european: Boolean): VehicleOverride = copy(
        connectorNames = base.connectorTypes(european).map { it.name },
        ccs1AdapterAvailable = base.defaultCcs1AdapterAvailability(european),
        connectorConfigurationSource = ConnectorConfigurationSource.CATALOG,
    )

    /** One-time strict inference for pre-provenance Android profiles. A connector difference is
     * treated as a user choice, so migration can never overwrite deliberately edited hardware. */
    fun withInferredConnectorSource(base: EvPreset, european: Boolean): VehicleOverride {
        if (connectorConfigurationSource != null) return this
        val catalogNames = base.connectorTypes(european).map { it.name }.toSet()
        val source = if (connectorNames.toSet() == catalogNames && ccs1AdapterAvailable != true) {
            ConnectorConfigurationSource.CATALOG
        } else {
            ConnectorConfigurationSource.USER
        }
        return copy(connectorConfigurationSource = source)
    }

    companion object {
        fun from(
            preset: EvPreset,
            batteryHealthPercent: Double = 100.0,
            ccs1AdapterAvailable: Boolean? = null,
            connectorConfigurationSource: ConnectorConfigurationSource? = null,
        ): VehicleOverride =
            VehicleOverride(
                batteryCapacityKwh = preset.batteryCapacityKwh,
                maxDcChargingKw = preset.maxDcChargingKw,
                efficiencyKwhPerKm = preset.efficiencyKwhPerKm,
                connectorNames = preset.connectorTypes.map { it.name },
                batteryHealthPercent = batteryHealthPercent,
                defaultArrivalBufferPercent = 15,
                ccs1AdapterAvailable = ccs1AdapterAvailable,
                connectorConfigurationSource = connectorConfigurationSource,
            )
    }
}

@Serializable
private data class VehicleOverrideDocument(val values: Map<String, VehicleOverride> = emptyMap())

@Serializable
private data class GarageVehicleDocument(val identifiers: List<String> = emptyList())

@Serializable
data class CustomVehicleRecord(
    val identifier: String = "custom:${UUID.randomUUID()}",
    val make: String,
    val model: String,
    val year: Int,
    val batteryCapacityKwh: Double,
    val maxDcChargingKw: Int,
    val efficiencyKwhPerKm: Double,
    val connectorNames: List<String>,
    /** Optional catalog record this editable profile started from. The profile keeps its own
     * stable identifier so two Garage entries can use the same published starting specs. */
    val sourceCatalogIdentifier: String? = null,
) {
    internal fun isValid(): Boolean =
        identifier.startsWith("custom:") && identifier.length <= 200 &&
            make.isNotBlank() && make.length <= 100 && model.isNotBlank() && model.length <= 150 &&
            year in 2010..(Calendar.getInstance().get(Calendar.YEAR) + 1) &&
            batteryCapacityKwh.isFinite() && batteryCapacityKwh in 15.0..300.0 &&
            maxDcChargingKw in 25..500 && efficiencyKwhPerKm.isFinite() &&
            efficiencyKwhPerKm > 0.05 && efficiencyKwhPerKm <= 0.50 &&
            (sourceCatalogIdentifier == null ||
                (sourceCatalogIdentifier.isNotBlank() && sourceCatalogIdentifier.length <= 200)) &&
            connectorNames.isNotEmpty() && connectorNames.size <= ConnectorType.entries.size &&
            connectorNames.distinct().size == connectorNames.size &&
            connectorNames.all { name -> ConnectorType.entries.any { it.name == name } }

    fun toPreset(): EvPreset {
        val source = EvCatalog.preset(sourceCatalogIdentifier)
        return EvPreset(
            make = make.trim(),
            model = model.trim(),
            year = year,
            batteryCapacityKwh = batteryCapacityKwh,
            maxDcChargingKw = maxDcChargingKw,
            efficiencyKwhPerKm = efficiencyKwhPerKm,
            connectorTypes = connectorNames.mapNotNull { name ->
                ConnectorType.entries.firstOrNull { it.name == name }
            },
            catalogIdentifier = identifier,
            ratedRangeKm = source?.ratedRangeKm,
            rangeStandard = source?.rangeStandard,
            sourceName = source?.sourceName,
            sourceUrl = source?.sourceUrl,
        )
    }
}

@Serializable
private data class CustomVehicleDocument(val values: List<CustomVehicleRecord> = emptyList())

@Serializable
data class SavedPlace(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val countryCode: String? = null,
) {
    internal fun isValid(): Boolean =
        name.isNotBlank() && name.length <= 200 && address.length <= 500 &&
            latitude.isFinite() && latitude in -90.0..90.0 &&
            longitude.isFinite() && longitude in -180.0..180.0 &&
            (countryCode == null || countryCode.matches(Regex("[A-Za-z]{2}")))
}

@Serializable
data class SavedTripSnapshot(
    val id: String,
    val name: String,
    val start: SavedPlace,
    val destination: SavedPlace,
    val waypoints: List<SavedPlace>,
    val currentSocPercent: Float,
    val arrivalBufferPercent: Float,
    val vehicleIdentifier: String,
    val weatherRangeLossPercent: Float,
    val extraLoadKg: Float,
    val drivingStyle: String,
    val minimumChargerSpeedKw: Int,
    val preferredNetworks: Set<String>,
    val avoidedNetworks: Set<String>,
    val avoidLowConfidenceStations: Boolean,
    val createdAtMillis: Long,
) {
    internal fun isValid(nowMillis: Long = System.currentTimeMillis()): Boolean =
        id.isNotBlank() && id.length <= 100 && name.isNotBlank() && name.length <= 300 &&
            start.isValid() && destination.isValid() &&
            waypoints.size <= MAX_USER_WAYPOINTS && waypoints.all(SavedPlace::isValid) &&
            currentSocPercent.isFinite() && currentSocPercent in 5f..100f &&
            arrivalBufferPercent.isFinite() && arrivalBufferPercent in 5f..35f &&
            vehicleIdentifier.isNotBlank() && vehicleIdentifier.length <= 200 &&
            weatherRangeLossPercent.isFinite() && weatherRangeLossPercent in 0f..45f &&
            extraLoadKg.isFinite() && extraLoadKg in 0f..750f &&
            minimumChargerSpeedKw in 50..350 &&
            preferredNetworks.isValidNetworkSet() && avoidedNetworks.isValidNetworkSet() &&
            createdAtMillis in 1L..(nowMillis + MAX_PERSISTED_CLOCK_SKEW_MILLIS)
}

private fun Set<String>.isValidNetworkSet(): Boolean =
    size <= 50 && all { it.isNotBlank() && it.length <= 100 }

@Serializable
private data class SavedTripDocument(val values: List<SavedTripSnapshot> = emptyList())

/** On-device preferences. Precise trip routes are intentionally not backed up (manifest policy). */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("evfr_settings", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    var hasOnboarded: Boolean
        get() = prefs.getBoolean(KEY_HAS_ONBOARDED, false)
        set(value) = prefs.edit { putBoolean(KEY_HAS_ONBOARDED, value) }

    var region: Region
        get() = Region.from(prefs.getString(KEY_REGION, null) ?: Locale.getDefault().country)
        set(value) = prefs.edit { putString(KEY_REGION, value.code) }

    var usesMiles: Boolean
        get() = if (prefs.contains(KEY_USES_MILES)) prefs.getBoolean(KEY_USES_MILES, false) else region.usesImperialByDefault
        set(value) = prefs.edit { putBoolean(KEY_USES_MILES, value) }

    var preferredNav: NavigationApp
        get() = NavigationApp.fromSerialized(prefs.getString(KEY_NAV, null))
        set(value) = prefs.edit { putString(KEY_NAV, value.serialized) }

    var selectedVehicleIdentifier: String?
        get() = prefs.getString(KEY_SELECTED_VEHICLE, null)
        set(value) = prefs.edit { putString(KEY_SELECTED_VEHICLE, value) }

    var garageVehicleIdentifiers: List<String>
        get() = prefs.getString(KEY_GARAGE_VEHICLES, null)?.let { encoded ->
            runCatching {
                json.decodeFromString(GarageVehicleDocument.serializer(), encoded).identifiers
                    .let(::normalizeGarageVehicleIdentifiers)
            }.getOrNull()
        } ?: emptyList()
        set(value) {
            val normalized = normalizeGarageVehicleIdentifiers(value)
            prefs.edit {
                putString(
                    KEY_GARAGE_VEHICLES,
                    json.encodeToString(
                        GarageVehicleDocument.serializer(),
                        GarageVehicleDocument(normalized),
                    ),
                )
            }
        }

    var customVehicles: List<CustomVehicleRecord>
        get() = prefs.getString(KEY_CUSTOM_VEHICLES, null)?.let { encoded ->
            runCatching {
                json.decodeFromString(CustomVehicleDocument.serializer(), encoded).values
                    .filter(CustomVehicleRecord::isValid)
                    .distinctBy(CustomVehicleRecord::identifier)
                    .take(MAX_GARAGE_VEHICLES)
            }.getOrNull()
        } ?: emptyList()
        set(value) {
            val normalized = value.filter(CustomVehicleRecord::isValid)
                .distinctBy(CustomVehicleRecord::identifier)
                .take(MAX_GARAGE_VEHICLES)
            prefs.edit {
                putString(
                    KEY_CUSTOM_VEHICLES,
                    json.encodeToString(CustomVehicleDocument.serializer(), CustomVehicleDocument(normalized)),
                )
            }
        }

    var weatherRangeLossPercent: Float
        get() = prefs.getFloat(KEY_WEATHER_LOSS, 0f).coerceIn(0f, 45f)
        set(value) = prefs.edit { putFloat(KEY_WEATHER_LOSS, value.coerceIn(0f, 45f)) }

    var extraLoadKg: Float
        get() = prefs.getFloat(KEY_EXTRA_LOAD, 0f).coerceIn(0f, 750f)
        set(value) = prefs.edit { putFloat(KEY_EXTRA_LOAD, value.coerceIn(0f, 750f)) }

    var drivingStyle: RangeDrivingStyle
        get() = RangeDrivingStyle.fromSerialized(prefs.getString(KEY_DRIVING_STYLE, null) ?: "balanced")
        set(value) = prefs.edit { putString(KEY_DRIVING_STYLE, value.serialized) }

    var minimumChargerSpeedKw: Int
        get() = prefs.getInt(KEY_MINIMUM_CHARGER_SPEED, 50).coerceIn(50, 350)
        set(value) = prefs.edit { putInt(KEY_MINIMUM_CHARGER_SPEED, value.coerceIn(50, 350)) }

    var arrivalBufferPercent: Float
        get() = prefs.getFloat(KEY_ARRIVAL_BUFFER, 15f).coerceIn(5f, 35f)
        set(value) = prefs.edit { putFloat(KEY_ARRIVAL_BUFFER, value.coerceIn(5f, 35f)) }

    var avoidLowConfidenceStations: Boolean
        get() = prefs.getBoolean(KEY_AVOID_LOW_CONFIDENCE, false)
        set(value) = prefs.edit { putBoolean(KEY_AVOID_LOW_CONFIDENCE, value) }

    var preferredNetworks: Set<String>
        get() = if (prefs.contains(KEY_PREFERRED_NETWORKS)) {
            prefs.getStringSet(KEY_PREFERRED_NETWORKS, emptySet())?.toSet() ?: emptySet()
        } else {
            region.defaultNetworks
        }
        set(value) = prefs.edit { putStringSet(KEY_PREFERRED_NETWORKS, normalizedNetworks(value)) }

    var avoidedNetworks: Set<String>
        get() = prefs.getStringSet(KEY_AVOIDED_NETWORKS, emptySet())?.toSet() ?: emptySet()
        set(value) = prefs.edit { putStringSet(KEY_AVOIDED_NETWORKS, normalizedNetworks(value)) }

    fun vehicleOverride(identifier: String): VehicleOverride? = vehicleOverrides()[identifier]

    fun setVehicleOverride(identifier: String, value: VehicleOverride?) {
        val updated = vehicleOverrides().toMutableMap()
        if (value == null || !value.isValid()) updated.remove(identifier) else updated[identifier] = value
        val encoded = json.encodeToString(VehicleOverrideDocument.serializer(), VehicleOverrideDocument(updated))
        prefs.edit { putString(KEY_VEHICLE_OVERRIDES, encoded) }
    }

    var savedTrips: List<SavedTripSnapshot>
        get() = prefs.getString(KEY_SAVED_TRIPS, null)?.let { encoded ->
            runCatching {
                json.decodeFromString(SavedTripDocument.serializer(), encoded).values
                    .filter { it.isValid() }
                    .sortedByDescending { it.createdAtMillis }
                    .take(MAX_SAVED_TRIPS)
            }.getOrNull()
        } ?: emptyList()
        set(value) {
            val limited = value.filter { it.isValid() }
                .sortedByDescending { it.createdAtMillis }
                .take(MAX_SAVED_TRIPS)
            prefs.edit {
                putString(
                    KEY_SAVED_TRIPS,
                    json.encodeToString(SavedTripDocument.serializer(), SavedTripDocument(limited)),
                )
            }
        }

    var navigationSession: NavigationSession?
        get() = prefs.getString(KEY_SESSION, null)?.let {
            runCatching { json.decodeFromString(NavigationSession.serializer(), it) }.getOrNull()
        }?.takeIf { NavigationSession.isRestorable(it, System.currentTimeMillis()) }
        set(value) {
            if (value == null) {
                prefs.edit { remove(KEY_SESSION) }
            } else {
                prefs.edit {
                    putString(KEY_SESSION, json.encodeToString(NavigationSession.serializer(), value))
                }
            }
        }

    /** Removes every app-owned preference. The view model immediately writes back only the
     * completed-onboarding flag and fresh starter Garage, mirroring iOS reset behavior. */
    fun resetAll() {
        prefs.edit { clear() }
    }

    private fun vehicleOverrides(): Map<String, VehicleOverride> =
        prefs.getString(KEY_VEHICLE_OVERRIDES, null)?.let { encoded ->
            runCatching {
                json.decodeFromString(VehicleOverrideDocument.serializer(), encoded).values
                    .filterKeys { it.isNotBlank() && it.length <= 200 }
                    .filterValues(VehicleOverride::isValid)
            }.getOrNull()
        } ?: emptyMap()

    private fun normalizedNetworks(values: Set<String>): Set<String> = values
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()

    private companion object {
        const val KEY_HAS_ONBOARDED = "evfr_has_onboarded"
        const val KEY_REGION = "evfr_region"
        const val KEY_USES_MILES = "evfr_uses_miles"
        const val KEY_NAV = "evfr_preferred_navigation_app"
        const val KEY_SESSION = "evfr_external_navigation_session"
        const val KEY_SELECTED_VEHICLE = "evfr_selected_vehicle"
        const val KEY_GARAGE_VEHICLES = "evfr_garage_vehicles"
        const val KEY_CUSTOM_VEHICLES = "evfr_custom_vehicles"
        const val KEY_VEHICLE_OVERRIDES = "evfr_vehicle_overrides"
        const val KEY_WEATHER_LOSS = "evfr_weather_range_loss"
        const val KEY_EXTRA_LOAD = "evfr_extra_load_kg"
        const val KEY_DRIVING_STYLE = "evfr_driving_style"
        const val KEY_MINIMUM_CHARGER_SPEED = "evfr_minimum_charger_speed"
        const val KEY_ARRIVAL_BUFFER = "evfr_arrival_buffer"
        const val KEY_AVOID_LOW_CONFIDENCE = "evfr_avoid_low_confidence"
        const val KEY_PREFERRED_NETWORKS = "evfr_preferred_networks"
        const val KEY_AVOIDED_NETWORKS = "evfr_avoided_networks"
        const val KEY_SAVED_TRIPS = "evfr_saved_trips"
        const val MAX_SAVED_TRIPS = 25
    }
}
