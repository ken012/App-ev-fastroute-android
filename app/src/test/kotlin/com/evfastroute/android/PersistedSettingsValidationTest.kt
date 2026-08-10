package com.evfastroute.android

import com.evfastroute.core.ConnectorType
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistedSettingsValidationTest {
    private val place = SavedPlace("Home", "1 Main Street", 45.0, -75.0)

    @Test
    fun vehicleOverrideRejectsUnsafeDecodedValues() {
        val valid = VehicleOverride(75.0, 200, 0.20, listOf(ConnectorType.CCS.name), 95.0)
        assertTrue(valid.isValid())
        assertFalse(valid.copy(batteryCapacityKwh = Double.NaN).isValid())
        assertFalse(valid.copy(efficiencyKwhPerKm = 0.0).isValid())
        assertFalse(valid.copy(batteryCapacityKwh = 14.9).isValid())
        assertFalse(valid.copy(maxDcChargingKw = 24).isValid())
        assertTrue(valid.copy(efficiencyKwhPerKm = 0.051).isValid())
        assertFalse(valid.copy(efficiencyKwhPerKm = 0.501).isValid())
        assertFalse(valid.copy(batteryHealthPercent = 59.9).isValid())
        assertFalse(valid.copy(defaultArrivalBufferPercent = 36).isValid())
        assertFalse(valid.copy(connectorNames = listOf("not-a-connector")).isValid())
    }

    @Test
    fun customVehicleUsesTheSameSafetyBoundsAsIos() {
        val valid = CustomVehicleRecord(
            identifier = "custom:test",
            make = "Example",
            model = "EV",
            year = 2026,
            batteryCapacityKwh = 75.0,
            maxDcChargingKw = 200,
            efficiencyKwhPerKm = 0.20,
            connectorNames = listOf(ConnectorType.CCS.name),
        )
        assertTrue(valid.isValid())
        assertEquals("2026 Example EV", valid.toPreset().displayName)
        assertFalse(valid.copy(year = 2009).isValid())
        assertFalse(valid.copy(year = Calendar.getInstance().get(Calendar.YEAR) + 2).isValid())
        assertFalse(valid.copy(efficiencyKwhPerKm = 0.05).isValid())
        assertFalse(valid.copy(connectorNames = emptyList()).isValid())
        assertFalse(valid.copy(sourceCatalogIdentifier = "x".repeat(201)).isValid())
    }

    @Test
    fun customGarageProfileKeepsItsOwnIdentityAndCatalogAttribution() {
        val source = com.evfastroute.core.EvCatalog.presets.first { it.sourceName != null }
        val profile = CustomVehicleRecord(
            identifier = "custom:driver-trim",
            make = source.make,
            model = source.model,
            year = source.year,
            batteryCapacityKwh = source.batteryCapacityKwh,
            maxDcChargingKw = source.maxDcChargingKw,
            efficiencyKwhPerKm = source.efficiencyKwhPerKm,
            connectorNames = source.connectorTypes.map { it.name },
            sourceCatalogIdentifier = source.catalogIdentifier,
        ).toPreset()

        assertEquals("custom:driver-trim", profile.catalogIdentifier)
        assertEquals(source.sourceName, profile.sourceName)
        assertEquals(source.sourceUrl, profile.sourceUrl)
        assertEquals(source.ratedRangeKm, profile.ratedRangeKm)
    }

    @Test
    fun savedTripRejectsInvalidCoordinatesAndOversizedPayloads() {
        val valid = SavedTripSnapshot(
            id = "trip-1",
            name = "Home to work",
            start = place,
            destination = place.copy(name = "Work", latitude = 46.0),
            waypoints = emptyList(),
            currentSocPercent = 80f,
            arrivalBufferPercent = 15f,
            vehicleIdentifier = "vehicle-1",
            weatherRangeLossPercent = 0f,
            extraLoadKg = 0f,
            drivingStyle = "balanced",
            minimumChargerSpeedKw = 50,
            preferredNetworks = emptySet(),
            avoidedNetworks = emptySet(),
            avoidLowConfidenceStations = false,
            createdAtMillis = 1_000L,
        )
        assertTrue(valid.isValid(nowMillis = 2_000L))
        assertFalse(valid.copy(start = place.copy(latitude = Double.NaN)).isValid(nowMillis = 2_000L))
        assertFalse(
            valid.copy(waypoints = List(MAX_USER_WAYPOINTS + 1) { place })
                .isValid(nowMillis = 2_000L),
        )
        assertFalse(valid.copy(currentSocPercent = Float.NaN).isValid(nowMillis = 2_000L))
        assertFalse(valid.copy(createdAtMillis = Long.MAX_VALUE).isValid(nowMillis = 2_000L))
    }

    @Test
    fun garageIdentifiersAreTrimmedDeduplicatedAndBounded() {
        val values = listOf(" car-a ", "car-a", "", "x".repeat(201)) +
            (0..MAX_GARAGE_VEHICLES).map { "car-$it" }

        val normalized = normalizeGarageVehicleIdentifiers(values)

        assertEquals(MAX_GARAGE_VEHICLES, normalized.size)
        assertEquals("car-a", normalized.first())
        assertEquals(normalized.distinct(), normalized)
    }

    @Test
    fun catalogReplacementUpdatesTheEditedGarageSlotWithoutDuplicating() {
        assertEquals(
            listOf("car-a", "car-new", "car-c"),
            replacingGarageVehicleIdentifier(
                values = listOf("car-a", "car-b", "car-c", "car-new"),
                original = "car-b",
                replacement = "car-new",
            ),
        )
        assertEquals(
            listOf("car-new", "car-a"),
            replacingGarageVehicleIdentifier(
                values = listOf("car-a"),
                original = null,
                replacement = "car-new",
            ),
        )
    }
}
