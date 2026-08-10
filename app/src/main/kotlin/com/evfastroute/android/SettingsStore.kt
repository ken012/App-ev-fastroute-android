package com.evfastroute.android

import android.content.Context
import com.evfastroute.core.ConnectorType
import com.evfastroute.core.EvPreset
import com.evfastroute.core.NavigationApp
import com.evfastroute.core.NavigationSession
import com.evfastroute.core.RangeDrivingStyle
import com.evfastroute.core.Region
import java.util.Locale
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

@Serializable
data class VehicleOverride(
    val batteryCapacityKwh: Double,
    val maxDcChargingKw: Int,
    val efficiencyKwhPerKm: Double,
    val connectorNames: List<String>,
    val batteryHealthPercent: Double,
) {
    internal fun isValid(): Boolean =
        batteryCapacityKwh.isFinite() && batteryCapacityKwh in 10.0..300.0 &&
            maxDcChargingKw in 20..500 &&
            efficiencyKwhPerKm.isFinite() && efficiencyKwhPerKm in 0.05..0.60 &&
            batteryHealthPercent.isFinite() && batteryHealthPercent in 50.0..100.0 &&
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

    companion object {
        fun from(preset: EvPreset, batteryHealthPercent: Double = 100.0): VehicleOverride =
            VehicleOverride(
                batteryCapacityKwh = preset.batteryCapacityKwh,
                maxDcChargingKw = preset.maxDcChargingKw,
                efficiencyKwhPerKm = preset.efficiencyKwhPerKm,
                connectorNames = preset.connectorTypes.map { it.name },
                batteryHealthPercent = batteryHealthPercent,
            )
    }
}

@Serializable
private data class VehicleOverrideDocument(val values: Map<String, VehicleOverride> = emptyMap())

@Serializable
private data class GarageVehicleDocument(val identifiers: List<String> = emptyList())

@Serializable
data class SavedPlace(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
) {
    internal fun isValid(): Boolean =
        name.isNotBlank() && name.length <= 200 && address.length <= 500 &&
            latitude.isFinite() && latitude in -90.0..90.0 &&
            longitude.isFinite() && longitude in -180.0..180.0
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
            arrivalBufferPercent.isFinite() && arrivalBufferPercent in 5f..40f &&
            vehicleIdentifier.isNotBlank() && vehicleIdentifier.length <= 200 &&
            weatherRangeLossPercent.isFinite() && weatherRangeLossPercent in 0f..45f &&
            extraLoadKg.isFinite() && extraLoadKg in 0f..750f &&
            minimumChargerSpeedKw in 0..350 &&
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
        set(value) = prefs.edit().putBoolean(KEY_HAS_ONBOARDED, value).apply()

    var prefersDarkMode: Boolean
        get() = if (prefs.contains(KEY_DARK_MODE)) prefs.getBoolean(KEY_DARK_MODE, true) else true
        set(value) = prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()

    var region: Region
        get() = Region.from(prefs.getString(KEY_REGION, null) ?: Locale.getDefault().country)
        set(value) = prefs.edit().putString(KEY_REGION, value.code).apply()

    var usesMiles: Boolean
        get() = if (prefs.contains(KEY_USES_MILES)) prefs.getBoolean(KEY_USES_MILES, false) else region.usesImperialByDefault
        set(value) = prefs.edit().putBoolean(KEY_USES_MILES, value).apply()

    var preferredNav: NavigationApp
        get() = NavigationApp.fromSerialized(prefs.getString(KEY_NAV, null))
        set(value) = prefs.edit().putString(KEY_NAV, value.serialized).apply()

    var selectedVehicleIdentifier: String?
        get() = prefs.getString(KEY_SELECTED_VEHICLE, null)
        set(value) = prefs.edit().putString(KEY_SELECTED_VEHICLE, value).apply()

    var garageVehicleIdentifiers: List<String>
        get() = prefs.getString(KEY_GARAGE_VEHICLES, null)?.let { encoded ->
            runCatching {
                json.decodeFromString(GarageVehicleDocument.serializer(), encoded).identifiers
                    .let(::normalizeGarageVehicleIdentifiers)
            }.getOrNull()
        } ?: emptyList()
        set(value) {
            val normalized = normalizeGarageVehicleIdentifiers(value)
            prefs.edit().putString(
                KEY_GARAGE_VEHICLES,
                json.encodeToString(
                    GarageVehicleDocument.serializer(),
                    GarageVehicleDocument(normalized),
                ),
            ).apply()
        }

    var weatherRangeLossPercent: Float
        get() = prefs.getFloat(KEY_WEATHER_LOSS, 0f).coerceIn(0f, 45f)
        set(value) = prefs.edit().putFloat(KEY_WEATHER_LOSS, value.coerceIn(0f, 45f)).apply()

    var extraLoadKg: Float
        get() = prefs.getFloat(KEY_EXTRA_LOAD, 0f).coerceIn(0f, 750f)
        set(value) = prefs.edit().putFloat(KEY_EXTRA_LOAD, value.coerceIn(0f, 750f)).apply()

    var drivingStyle: RangeDrivingStyle
        get() = RangeDrivingStyle.fromSerialized(prefs.getString(KEY_DRIVING_STYLE, null) ?: "balanced")
        set(value) = prefs.edit().putString(KEY_DRIVING_STYLE, value.serialized).apply()

    var minimumChargerSpeedKw: Int
        get() = prefs.getInt(KEY_MINIMUM_CHARGER_SPEED, 50).coerceIn(0, 350)
        set(value) = prefs.edit().putInt(KEY_MINIMUM_CHARGER_SPEED, value.coerceIn(0, 350)).apply()

    var avoidLowConfidenceStations: Boolean
        get() = prefs.getBoolean(KEY_AVOID_LOW_CONFIDENCE, false)
        set(value) = prefs.edit().putBoolean(KEY_AVOID_LOW_CONFIDENCE, value).apply()

    var preferredNetworks: Set<String>
        get() = prefs.getStringSet(KEY_PREFERRED_NETWORKS, emptySet())?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_PREFERRED_NETWORKS, normalizedNetworks(value)).apply()

    var avoidedNetworks: Set<String>
        get() = prefs.getStringSet(KEY_AVOIDED_NETWORKS, emptySet())?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_AVOIDED_NETWORKS, normalizedNetworks(value)).apply()

    fun vehicleOverride(identifier: String): VehicleOverride? = vehicleOverrides()[identifier]

    fun setVehicleOverride(identifier: String, value: VehicleOverride?) {
        val updated = vehicleOverrides().toMutableMap()
        if (value == null || !value.isValid()) updated.remove(identifier) else updated[identifier] = value
        val encoded = json.encodeToString(VehicleOverrideDocument.serializer(), VehicleOverrideDocument(updated))
        prefs.edit().putString(KEY_VEHICLE_OVERRIDES, encoded).apply()
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
            prefs.edit().putString(
                KEY_SAVED_TRIPS,
                json.encodeToString(SavedTripDocument.serializer(), SavedTripDocument(limited)),
            ).apply()
        }

    var navigationSession: NavigationSession?
        get() = prefs.getString(KEY_SESSION, null)?.let {
            runCatching { json.decodeFromString(NavigationSession.serializer(), it) }.getOrNull()
        }?.takeIf { NavigationSession.isRestorable(it, System.currentTimeMillis()) }
        set(value) {
            if (value == null) {
                prefs.edit().remove(KEY_SESSION).apply()
            } else {
                prefs.edit().putString(KEY_SESSION, json.encodeToString(NavigationSession.serializer(), value)).apply()
            }
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
        const val KEY_DARK_MODE = "evfr_dark_mode"
        const val KEY_REGION = "evfr_region"
        const val KEY_USES_MILES = "evfr_uses_miles"
        const val KEY_NAV = "evfr_preferred_navigation_app"
        const val KEY_SESSION = "evfr_external_navigation_session"
        const val KEY_SELECTED_VEHICLE = "evfr_selected_vehicle"
        const val KEY_GARAGE_VEHICLES = "evfr_garage_vehicles"
        const val KEY_VEHICLE_OVERRIDES = "evfr_vehicle_overrides"
        const val KEY_WEATHER_LOSS = "evfr_weather_range_loss"
        const val KEY_EXTRA_LOAD = "evfr_extra_load_kg"
        const val KEY_DRIVING_STYLE = "evfr_driving_style"
        const val KEY_MINIMUM_CHARGER_SPEED = "evfr_minimum_charger_speed"
        const val KEY_AVOID_LOW_CONFIDENCE = "evfr_avoid_low_confidence"
        const val KEY_PREFERRED_NETWORKS = "evfr_preferred_networks"
        const val KEY_AVOIDED_NETWORKS = "evfr_avoided_networks"
        const val KEY_SAVED_TRIPS = "evfr_saved_trips"
        const val MAX_SAVED_TRIPS = 25
    }
}
