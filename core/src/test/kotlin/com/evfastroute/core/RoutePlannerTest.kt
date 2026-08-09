package com.evfastroute.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoutePlannerTest {

    private val vehicle = Vehicle(
        batteryCapacityKwh = 75.0, efficiencyKwhPerKm = 0.17, maxDcChargingKw = 250,
        connectorTypes = listOf(ConnectorType.CCS),
    )

    private fun charger(id: String) = Charger(
        id = id, name = id, network = "Net", latitude = 0.0, longitude = 0.0,
        connectorTypes = listOf(ConnectorType.CCS), maxKw = 150, numberOfStalls = 4,
        reliabilityScore = 90.0, pricePerKwh = 0.5,
    )

    @Test
    fun buildRouteWalksStateOfChargeAndCharges() {
        val route = RoutePlanner.buildRoute(
            id = "r1", sequence = listOf(charger("A")),
            legDistancesKm = listOf(300.0, 300.0), legDurationMinutes = listOf(180, 180),
            directMinutes = 360, vehicle = vehicle, currentSOC = 80.0, arrivalBufferPercent = 10.0,
        )!!
        assertEquals(1, route.chargingStops.size)
        assertEquals(12, route.chargingStops[0].arrivalBatteryPercent)   // 80% − 300·socPerKm
        assertEquals(78, route.chargingStops[0].targetBatteryPercent)    // enough to reach dest + buffer
        assertEquals(10, route.arrivalBatteryPercent)
        assertTrue(route.chargingMinutes > 0)
        assertEquals(360 + route.chargingMinutes, route.totalEtaMinutes)
        assertEquals(1, route.itinerary.size)
        assertEquals(ItineraryStop.Kind.CHARGING, route.itinerary[0].kind)
        assertEquals(180, route.itinerary[0].arrivalMinutesFromStart)
    }

    @Test
    fun infeasibleWhenCannotReachTheFirstCharger() {
        assertNull(
            RoutePlanner.buildRoute(
                id = "r", sequence = listOf(charger("A")),
                legDistancesKm = listOf(300.0, 300.0), legDurationMinutes = listOf(180, 180),
                directMinutes = 360, vehicle = vehicle, currentSOC = 12.0, arrivalBufferPercent = 10.0,
            ),
        )
    }

    @Test
    fun optimizeMergesObjectivesThatShareASequence() {
        val candidate = RoutePlanner.buildRoute(
            id = "r1", sequence = listOf(charger("A")),
            legDistancesKm = listOf(300.0, 300.0), legDurationMinutes = listOf(180, 180),
            directMinutes = 360, vehicle = vehicle, currentSOC = 80.0, arrivalBufferPercent = 10.0,
        )!!
        val options = RoutePlanner.optimize(listOf(candidate))
        // A single winning sequence is deduped into one option that supports every objective it won.
        assertEquals(1, options.size)
        assertTrue(options[0].supportedObjectives.contains(RouteObjective.FASTEST))
        assertTrue(options[0].supportedObjectives.contains(RouteObjective.FEWEST_STOPS))
        assertEquals(RouteObjective.FASTEST, options[0].objective) // first planner objective labels it
    }
}
