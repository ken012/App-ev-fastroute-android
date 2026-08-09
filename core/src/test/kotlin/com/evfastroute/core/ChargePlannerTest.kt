package com.evfastroute.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ports the iOS ChargePlanTests so the Android charging model is provably at the same standard.
 * Values are computed from the shared logic and must match the Swift suite's expectations.
 */
class ChargePlannerTest {

    // Typical mid-size EV: 75 kWh usable, ~0.17 kWh/km.
    private val capacity = 75.0
    private val efficiency = 0.17

    @Test
    fun noStopsForShortTripOnFullBattery() {
        val legs = ChargePlanner.planLegs(
            distanceKm = 120.0, capacityKwh = capacity, efficiencyKwhPerKm = efficiency,
            currentSOC = 90.0, arrivalBufferPercent = 10.0,
        )
        assertTrue(legs.isEmpty())
    }

    @Test
    fun longTripNeedsAtLeastOneStop() {
        val legs = ChargePlanner.planLegs(
            distanceKm = 600.0, capacityKwh = capacity, efficiencyKwhPerKm = efficiency,
            currentSOC = 80.0, arrivalBufferPercent = 10.0,
        )
        assertTrue(legs.isNotEmpty())
    }

    @Test
    fun chargeMinutesHasAFiveMinuteFloor() {
        // A tiny top-up still returns the minimum session length.
        assertEquals(5, ChargePlanner.chargeMinutes(fromSOC = 50, toSOC = 51, capacityKwh = capacity, effectiveKw = 150.0))
        assertTrue(ChargePlanner.chargeMinutes(fromSOC = 20, toSOC = 80, capacityKwh = capacity, effectiveKw = 150.0) > 5)
    }

    @Test
    fun highStateOfChargeTaperIsMaterial() {
        // The same 15-point span costs more time in the high-SOC taper region than at low SOC.
        val lowBand = ChargePlanner.chargeMinutes(fromSOC = 20, toSOC = 35, capacityKwh = capacity, effectiveKw = 150.0)
        val highBand = ChargePlanner.chargeMinutes(fromSOC = 80, toSOC = 95, capacityKwh = capacity, effectiveKw = 150.0)
        assertTrue(highBand > lowBand, "80->95% must be slower than 20->35% (got $highBand vs $lowBand)")
    }

    @Test
    fun energyAddedMatchesCapacityFraction() {
        assertEquals(45.0, ChargePlanner.energyAdded(fromSOC = 20, toSOC = 80, capacityKwh = 75.0), 1e-9)
        assertEquals(0.0, ChargePlanner.energyAdded(fromSOC = 80, toSOC = 80, capacityKwh = 75.0), 1e-9)
    }

    @Test
    fun arrivalSOCWithoutStopsIsLinearDrain() {
        val soc = ChargePlanner.arrivalSOC(
            distanceKm = 100.0, capacityKwh = capacity, efficiencyKwhPerKm = efficiency,
            currentSOC = 90.0, legs = emptyList(),
        )
        // 100 km * (0.17/75*100) ≈ 22.7% drain → ~67%.
        assertEquals(67, soc)
    }

    @Test
    fun degenerateInputsReturnEmptyOrZero() {
        assertTrue(
            ChargePlanner.planLegs(
                distanceKm = 0.0, capacityKwh = capacity, efficiencyKwhPerKm = efficiency,
                currentSOC = 50.0, arrivalBufferPercent = 10.0,
            ).isEmpty()
        )
        assertEquals(0, ChargePlanner.chargeMinutes(fromSOC = 80, toSOC = 80, capacityKwh = capacity, effectiveKw = 150.0))
        assertTrue(abs(ChargePlanner.energyAdded(fromSOC = 90, toSOC = 80, capacityKwh = 75.0)) < 1e-9)
    }
}
