package com.evfastroute.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Locks the multi-stop (through-waypoints) SOC walk: charging only where needed, driving through
 * user stops, and correct interleaved segment indices. Ported from iOS buildRouteThroughWaypoints. */
class MultiStopRouteTest {

    private val vehicle = Vehicle(
        batteryCapacityKwh = 75.0, efficiencyKwhPerKm = 0.17, maxDcChargingKw = 250,
        connectorTypes = listOf(ConnectorType.CCS),
    )

    private fun charger(id: String) = Charger(
        id = id, name = id, network = "Net", latitude = 0.0, longitude = 0.0,
        connectorTypes = listOf(ConnectorType.CCS), maxKw = 150, numberOfStalls = 4,
        reliabilityScore = 90.0, pricePerKwh = 0.5,
    )

    private fun waypoint(name: String) =
        PlaceCandidate(placeName = name, fullAddress = "$name St", latitude = 1.0, longitude = 1.0, distanceKm = null)

    @Test
    fun chargerOnlyMatchesTheSingleLegBuilder() {
        val through = RoutePlanner.buildRouteThroughWaypoints(
            id = "r1",
            vias = listOf(PlannedVia(charger = charger("A"), userWaypointIndex = null, name = "A")),
            userWaypoints = emptyList(),
            legDistancesKm = listOf(300.0, 300.0), legDurationMinutes = listOf(180, 180),
            directMinutes = 360, vehicle = vehicle, currentSOC = 80.0, arrivalBufferPercent = 10.0,
        )!!
        val simple = RoutePlanner.buildRoute(
            id = "r1", sequence = listOf(charger("A")),
            legDistancesKm = listOf(300.0, 300.0), legDurationMinutes = listOf(180, 180),
            directMinutes = 360, vehicle = vehicle, currentSOC = 80.0, arrivalBufferPercent = 10.0,
        )!!
        assertEquals(simple.chargingStops, through.chargingStops)
        assertEquals(simple.arrivalBatteryPercent, through.arrivalBatteryPercent)
        assertEquals(simple.totalEtaMinutes, through.totalEtaMinutes)
        assertEquals(listOf(0), through.stopSegmentIndices)
        assertTrue(through.userWaypoints.isEmpty())
    }

    @Test
    fun drivesThroughAUserStopThenChargesOnce() {
        val route = RoutePlanner.buildRouteThroughWaypoints(
            id = "r2",
            vias = listOf(
                PlannedVia(charger = null, userWaypointIndex = 0, name = "Coffee"),
                PlannedVia(charger = charger("A"), userWaypointIndex = null, name = "A"),
            ),
            userWaypoints = listOf(waypoint("Coffee")),
            legDistancesKm = listOf(100.0, 150.0, 250.0), legDurationMinutes = listOf(60, 90, 150),
            directMinutes = 300, vehicle = vehicle, currentSOC = 80.0, arrivalBufferPercent = 10.0,
        )!!
        assertEquals(1, route.chargingStops.size)
        assertEquals(23, route.chargingStops[0].arrivalBatteryPercent)   // 80 − 100·s − 150·s
        assertEquals(67, route.chargingStops[0].targetBatteryPercent)    // enough to reach dest + buffer
        assertEquals(10, route.arrivalBatteryPercent)
        assertEquals(listOf(ItineraryStop.Kind.VISIT, ItineraryStop.Kind.CHARGING), route.itinerary.map { it.kind })
        assertEquals(57, route.itinerary[0].arrivalBatteryPercent)       // battery when reaching the coffee stop
        assertEquals(listOf(0), route.userWaypointSegmentIndices)        // waypoint reached at end of leg 0
        assertEquals(listOf(1), route.stopSegmentIndices)                // charger reached at end of leg 1
        assertEquals(1, route.userWaypoints.size)
    }

    @Test
    fun chargerBeforeAWaypointReservesEnoughToReachTheNextCharger() {
        // start → A(charge) → W(visit) → B(charge) → dest. A must carry enough (10% reserve) to
        // reach B *through* the intervening visit, not just to the visit.
        val route = RoutePlanner.buildRouteThroughWaypoints(
            id = "r3",
            vias = listOf(
                PlannedVia(charger = charger("A"), userWaypointIndex = null, name = "A"),
                PlannedVia(charger = null, userWaypointIndex = 0, name = "W"),
                PlannedVia(charger = charger("B"), userWaypointIndex = null, name = "B"),
            ),
            userWaypoints = listOf(waypoint("W")),
            legDistancesKm = listOf(250.0, 100.0, 100.0, 150.0),
            legDurationMinutes = listOf(150, 60, 60, 90),
            directMinutes = 360, vehicle = vehicle, currentSOC = 90.0, arrivalBufferPercent = 10.0,
        )!!
        assertEquals(2, route.chargingStops.size)
        assertEquals(33, route.chargingStops[0].arrivalBatteryPercent)
        assertEquals(56, route.chargingStops[0].targetBatteryPercent)   // sized to reach B across the visit
        assertEquals(11, route.chargingStops[1].arrivalBatteryPercent)
        assertEquals(44, route.chargingStops[1].targetBatteryPercent)
        assertEquals(listOf(0, 2), route.stopSegmentIndices)
        assertEquals(listOf(1), route.userWaypointSegmentIndices)
        assertEquals(
            listOf(ItineraryStop.Kind.CHARGING, ItineraryStop.Kind.VISIT, ItineraryStop.Kind.CHARGING),
            route.itinerary.map { it.kind },
        )
        assertEquals(33, route.itinerary[1].arrivalBatteryPercent)      // battery at the visit
        assertEquals(10, route.arrivalBatteryPercent)
    }

    @Test
    fun infeasibleWhenAStopCannotBeReachedAboveFivePercent() {
        assertNull(
            RoutePlanner.buildRouteThroughWaypoints(
                id = "r4",
                vias = listOf(
                    PlannedVia(charger = null, userWaypointIndex = 0, name = "Far"),
                    PlannedVia(charger = charger("A"), userWaypointIndex = null, name = "A"),
                ),
                userWaypoints = listOf(waypoint("Far")),
                legDistancesKm = listOf(100.0, 300.0, 200.0), legDurationMinutes = listOf(60, 180, 120),
                directMinutes = 360, vehicle = vehicle, currentSOC = 80.0, arrivalBufferPercent = 10.0,
            ),
        )
    }

    @Test
    fun mismatchedLegCountsReturnNull() {
        assertNull(
            RoutePlanner.buildRouteThroughWaypoints(
                id = "r5",
                vias = listOf(PlannedVia(charger = charger("A"), userWaypointIndex = null, name = "A")),
                userWaypoints = emptyList(),
                legDistancesKm = listOf(300.0), legDurationMinutes = listOf(180),
                directMinutes = 180, vehicle = vehicle, currentSOC = 80.0, arrivalBufferPercent = 10.0,
            ),
        )
    }
}
