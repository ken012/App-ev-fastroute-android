package com.evfastroute.android

import com.evfastroute.core.ConnectorType
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
        assertFalse(valid.copy(connectorNames = listOf("not-a-connector")).isValid())
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
}
