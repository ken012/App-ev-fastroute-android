package com.evfastroute.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Ports the iOS EnergyPlanTests so the feasibility model matches across platforms. */
class EnergyModelTest {

    private val capacity = 75.0
    private val efficiency = 0.17

    @Test
    fun noChargeNeededForShortTripOnFullBattery() {
        val plan = EnergyModel.energyPlan(
            distanceKm = 120.0, capacityKwh = capacity, efficiencyKwhPerKm = efficiency,
            currentBatteryPercent = 90.0, arrivalBufferPercent = 10.0,
        )
        assertFalse(plan.needsCharge)
        assertEquals(62, plan.arrivalIfNoChargePct)
    }

    @Test
    fun chargeNeededForLongTrip() {
        val plan = EnergyModel.energyPlan(
            distanceKm = 600.0, capacityKwh = capacity, efficiencyKwhPerKm = efficiency,
            currentBatteryPercent = 80.0, arrivalBufferPercent = 10.0,
        )
        assertTrue(plan.needsCharge)
    }

    @Test
    fun drainAndArrivalAreClamped() {
        val plan = EnergyModel.energyPlan(
            distanceKm = 2000.0, capacityKwh = capacity, efficiencyKwhPerKm = efficiency,
            currentBatteryPercent = 50.0, arrivalBufferPercent = 10.0,
        )
        assertEquals(0, plan.arrivalIfNoChargePct)
        assertEquals(100.0, plan.tripDrainPct, 1e-9)
    }
}
