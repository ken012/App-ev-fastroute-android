package com.evfastroute.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ports the iOS RangeEstimator assertions so range math matches across platforms. */
class RangeEstimatorTest {

    private val vehicle = Vehicle(
        batteryCapacityKwh = 75.0, efficiencyKwhPerKm = 0.17, maxDcChargingKw = 250,
        connectorTypes = listOf(ConnectorType.CCS),
    )

    @Test
    fun weatherRangeLossReducesExpectedRangeProportionally() {
        val none = RangeEstimator.estimate(vehicle, 80.0, 15.0, 0.0, 0.0, RangeDrivingStyle.BALANCED)
        val cold = RangeEstimator.estimate(vehicle, 80.0, 15.0, 20.0, 0.0, RangeDrivingStyle.BALANCED)
        // 20% range loss ⇒ ×1/(1-0.20) consumption ⇒ 0.8× range.
        assertEquals(0.8, cold.expectedRangeKm / none.expectedRangeKm, 0.01)
    }

    @Test
    fun batteryHealthScalesUsableCapacity() {
        val healthy = RangeEstimator.estimate(vehicle, 80.0, 15.0, 0.0, 0.0, RangeDrivingStyle.BALANCED)
        val degraded = RangeEstimator.estimate(
            vehicle.copy(batteryHealthPercent = 80.0), 80.0, 15.0, 0.0, 0.0, RangeDrivingStyle.BALANCED,
        )
        assertEquals(75.0, healthy.usableCapacityKwh, 1e-9)
        assertEquals(60.0, degraded.usableCapacityKwh, 1e-9)
    }

    @Test
    fun highwaySpeedRaisesConsumptionCityLowersItAndNullIsNeutral() {
        assertTrue(RangeEstimator.speedConsumptionMultiplier(120.0) > 1.0)
        assertTrue(RangeEstimator.speedConsumptionMultiplier(30.0) < 1.0)
        assertEquals(1.0, RangeEstimator.speedConsumptionMultiplier(null), 1e-9)
    }

    @Test
    fun planningBandIsMoreConservativeThanExpected() {
        val e = RangeEstimator.estimate(vehicle, 80.0, 15.0, 0.0, 0.0, RangeDrivingStyle.BALANCED)
        assertTrue(e.planningEfficiencyKwhPerKm > e.expectedEfficiencyKwhPerKm)
        assertTrue(e.conservativeRangeKm < e.expectedRangeKm)
    }

    @Test
    fun averageSpeedIsBoundedAndValidated() {
        assertNull(RangeEstimator.averageSpeedKph(0.0, 3600.0))
        assertNull(RangeEstimator.averageSpeedKph(100.0, 0.0))
        assertEquals(100.0, RangeEstimator.averageSpeedKph(100.0, 3600.0)!!, 1e-6)
        assertEquals(140.0, RangeEstimator.averageSpeedKph(1000.0, 3600.0)!!, 1e-6) // clamped
    }
}
