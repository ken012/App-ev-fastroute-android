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
        availableStalls = 2, status = ChargerStatus.AVAILABLE,
        reliabilityScore = 90.0, pricePerKwh = 0.5, priceCurrencyCode = "USD",
        usageCostText = "USD 0.50/kWh", detourMinutes = 7, region = "CA",
        dataProviderTitle = "Test provider", dataProviderLicense = "CC BY 4.0",
        dataProviderWebsiteUrl = "https://example.com/provider",
    )

    @Test
    fun buildRouteWalksStateOfChargeAndCharges() {
        val step = DrivingStep("Continue straight", 100.0, LatLon(1.0, 2.0))
        val route = RoutePlanner.buildRoute(
            id = "r1", sequence = listOf(charger("A")),
            legDistancesKm = listOf(300.0, 300.0), legDurationMinutes = listOf(180, 180),
            directMinutes = 360, vehicle = vehicle, currentSOC = 80.0, arrivalBufferPercent = 10.0,
            legSteps = listOf(listOf(step), listOf(step)),
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
        assertEquals("Test provider", route.chargingStops[0].dataProviderTitle)
        assertEquals("CC BY 4.0", route.chargingStops[0].dataProviderLicense)
        assertEquals("https://example.com/provider", route.chargingStops[0].dataProviderWebsiteUrl)
        assertEquals("Net", route.chargingStops[0].network)
        assertEquals(listOf(ConnectorType.CCS), route.chargingStops[0].connectorTypes)
        assertEquals(150, route.chargingStops[0].maxKw)
        assertEquals(4, route.chargingStops[0].numberOfStalls)
        assertEquals(2, route.chargingStops[0].availableStalls)
        assertEquals(ChargerStatus.AVAILABLE, route.chargingStops[0].status)
        assertEquals("USD", route.chargingStops[0].priceCurrencyCode)
        assertEquals("USD 0.50/kWh", route.chargingStops[0].usageCostText)
        assertEquals(7, route.chargingStops[0].detourMinutes)
        assertEquals("CA", route.chargingStops[0].region)
        assertEquals(ChargerDataSource.OPEN_CHARGE_MAP, route.chargingStops[0].dataSource)
        assertEquals("\$24.75", route.estimatedCostText)
        assertEquals(listOf(0, 1), route.routeSteps.map { it.segmentIndex })
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
    fun ccsStopRequiresExplicitAdapterConfirmationForNacsVehicle() {
        val unconfirmed = vehicle.copy(
            connectorTypes = listOf(ConnectorType.NACS, ConnectorType.CCS),
            ccs1AdapterAvailable = null,
        )
        val arguments = listOf(charger("CCS"))
        assertNull(
            RoutePlanner.buildRoute(
                id = "safe", sequence = arguments,
                legDistancesKm = listOf(300.0, 300.0), legDurationMinutes = listOf(180, 180),
                directMinutes = 360, vehicle = unconfirmed,
                currentSOC = 80.0, arrivalBufferPercent = 10.0,
            ),
        )
        assertTrue(
            RoutePlanner.buildRoute(
                id = "confirmed", sequence = arguments,
                legDistancesKm = listOf(300.0, 300.0), legDurationMinutes = listOf(180, 180),
                directMinutes = 360, vehicle = unconfirmed.copy(ccs1AdapterAvailable = true),
                currentSOC = 80.0, arrivalBufferPercent = 10.0,
            ) != null,
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

    @Test
    fun chargeTargetHasNoSixtyPercentFloor() {
        // Reaching the charger at 23% and needing ~33% to finish must target ~33%, NOT 60%.
        // (An earlier Android-only 60% floor over-charged here and diverged from iOS.)
        val route = RoutePlanner.buildRoute(
            id = "r", sequence = listOf(charger("A")),
            legDistancesKm = listOf(250.0, 100.0), legDurationMinutes = listOf(150, 60),
            directMinutes = 210, vehicle = vehicle, currentSOC = 80.0, arrivalBufferPercent = 10.0,
        )!!
        assertEquals(23, route.chargingStops[0].arrivalBatteryPercent)
        assertEquals(33, route.chargingStops[0].targetBatteryPercent) // reach dest + buffer, no floor
    }

    @Test
    fun belowFloorRouteMatchesTheMultiStopBuilder() {
        // The single-leg and through-waypoints builders must agree even when the target is under 60%.
        val single = RoutePlanner.buildRoute(
            id = "r", sequence = listOf(charger("A")),
            legDistancesKm = listOf(250.0, 100.0), legDurationMinutes = listOf(150, 60),
            directMinutes = 210, vehicle = vehicle, currentSOC = 80.0, arrivalBufferPercent = 10.0,
        )!!
        val through = RoutePlanner.buildRouteThroughWaypoints(
            id = "r", vias = listOf(PlannedVia(charger = charger("A"), userWaypointIndex = null, name = "A")),
            userWaypoints = emptyList(),
            legDistancesKm = listOf(250.0, 100.0), legDurationMinutes = listOf(150, 60),
            directMinutes = 210, vehicle = vehicle, currentSOC = 80.0, arrivalBufferPercent = 10.0,
        )!!
        assertEquals(single.chargingStops, through.chargingStops)
    }

    private fun candidate(
        id: String,
        eta: Int,
        cost: Double,
        risk: Double,
        stops: List<String>,
        currency: String = "USD",
    ) = RouteOption(
        id = id, objective = RouteObjective.VERIFIED, supportedObjectives = emptySet(),
        title = "c", mode = "c", totalEtaMinutes = eta, drivingMinutes = eta, chargingMinutes = 0,
        detourMinutes = 0, arrivalBatteryPercent = 20, riskScore = risk,
        chargingStops = stops.map { ChargingStop(it, it, 0.0, 0.0, 20, 60, 10) },
        itinerary = emptyList(), estimatedChargingCostValue = cost,
        estimatedChargingCostCurrencyCode = currency,
    )

    @Test
    fun optimizePicksCorrectWinnerPerObjective() {
        val fastPricey = candidate("Y", eta = 300, cost = 30.0, risk = 20.0, stops = listOf("c3")) // fast, 1 stop
        val slowCheap = candidate("X", eta = 400, cost = 10.0, risk = 5.0, stops = listOf("c1", "c2")) // cheap, reliable
        val options = RoutePlanner.optimize(listOf(fastPricey, slowCheap))

        val fastest = options.first { RouteObjective.FASTEST in it.supportedObjectives }
        assertEquals(300, fastest.totalEtaMinutes)                 // FASTEST → lowest ETA
        assertEquals(1, fastest.chargingStops.size)
        assertTrue(RouteObjective.FEWEST_STOPS in fastest.supportedObjectives) // fewest stops → same route

        val cheapest = options.first { RouteObjective.LOWEST_COST in it.supportedObjectives }
        assertEquals(10.0, cheapest.estimatedChargingCostValue)    // LOWEST_COST → lowest cost
        assertTrue(RouteObjective.RELIABLE in cheapest.supportedObjectives) // RELIABLE → lowest risk
    }

    @Test
    fun rejectsPhantomZeroChargeStop() {
        // Arriving with exactly enough charge to continue must drop the candidate, not emit a 70→70 stop.
        val bigBattery = Vehicle(
            batteryCapacityKwh = 100.0, efficiencyKwhPerKm = 0.2, maxDcChargingKw = 250,
            connectorTypes = listOf(ConnectorType.CCS),
        )
        assertNull(
            RoutePlanner.buildRoute(
                id = "r", sequence = listOf(charger("A")),
                legDistancesKm = listOf(100.0, 100.0), legDurationMinutes = listOf(60, 60),
                directMinutes = 120, vehicle = bigBattery, currentSOC = 90.0, arrivalBufferPercent = 10.0,
            ),
        )
    }

    @Test
    fun unknownOrMixedCurrencyCostIsNotPresentedAsAComparableEstimate() {
        val unknown = charger("unknown").copy(pricePerKwh = null, priceCurrencyCode = null)
        val unknownRoute = RoutePlanner.buildRoute(
            id = "u", sequence = listOf(unknown),
            legDistancesKm = listOf(300.0, 300.0), legDurationMinutes = listOf(180, 180),
            directMinutes = 360, vehicle = vehicle, currentSOC = 80.0, arrivalBufferPercent = 10.0,
        )!!
        assertNull(unknownRoute.estimatedChargingCostValue)
        assertTrue(RouteObjective.LOWEST_COST !in RoutePlanner.optimize(listOf(unknownRoute)).flatMap { it.supportedObjectives })

        val usd = candidate("usd", eta = 100, cost = 20.0, risk = 4.0, stops = listOf("u"), currency = "USD")
        val cad = candidate("cad", eta = 110, cost = 10.0, risk = 5.0, stops = listOf("c"), currency = "CAD")
        val mixedOptions = RoutePlanner.optimize(listOf(usd, cad))
        assertTrue(mixedOptions.none { RouteObjective.LOWEST_COST in it.supportedObjectives })

        val missingCurrency = usd.copy(estimatedChargingCostCurrencyCode = null)
        val incompleteOptions = RoutePlanner.optimize(listOf(missingCurrency))
        assertTrue(incompleteOptions.none { RouteObjective.LOWEST_COST in it.supportedObjectives })
    }
}
