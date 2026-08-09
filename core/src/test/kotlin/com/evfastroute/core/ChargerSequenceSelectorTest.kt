package com.evfastroute.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies the beam-search parity behavior: reachability gating, fewest-stops = count-first, and
 * deterministic output. Mirrors the intent of the iOS selectChargerSequences tests.
 */
class ChargerSequenceSelectorTest {

    // 75 kWh, 0.17 kWh/km → ~0.2267 %/km. From 80%: ~331 km reachable; after a 95% charge: ~375 km.
    private val vehicle = Vehicle(
        batteryCapacityKwh = 75.0, efficiencyKwhPerKm = 0.17, maxDcChargingKw = 250,
        connectorTypes = listOf(ConnectorType.CCS),
    )

    private fun projected(id: String, progressKm: Double) = ProjectedCharger(
        charger = Charger(
            id = id, name = id, network = "Net", latitude = 0.0, longitude = 0.0,
            connectorTypes = listOf(ConnectorType.CCS), maxKw = 150, numberOfStalls = 4,
        ),
        progressKm = progressKm, corridorKm = 1.0,
    )

    // A reachable in one hop and able to complete the 600 km trip alone; B/C only via a first stop.
    private val chargers = listOf(projected("A", 300.0), projected("B", 350.0), projected("C", 550.0))

    @Test
    fun fewestStopsReturnsTheMinimumStopSequenceFirst() {
        val sequences = ChargerSequenceSelector.selectChargerSequences(
            projected = chargers, totalDistanceKm = 600.0, vehicle = vehicle,
            currentSOC = 80.0, arrivalBufferPercent = 10.0,
            prefer = { 0.0 }, objective = RouteObjective.FEWEST_STOPS,
        )
        assertTrue(sequences.isNotEmpty())
        assertEquals(1, sequences.first().size)
        assertEquals("A", sequences.first().first().id)
    }

    @Test
    fun unreachableTripYieldsNoSequence() {
        // Start at 12% → can't even reach the first charger.
        val sequences = ChargerSequenceSelector.selectChargerSequences(
            projected = chargers, totalDistanceKm = 600.0, vehicle = vehicle,
            currentSOC = 12.0, arrivalBufferPercent = 10.0,
            prefer = { 0.0 }, objective = RouteObjective.FEWEST_STOPS,
        )
        assertTrue(sequences.isEmpty())
    }

    @Test
    fun outputIsDeterministic() {
        fun run() = ChargerSequenceSelector.selectChargerSequences(
            projected = chargers, totalDistanceKm = 600.0, vehicle = vehicle,
            currentSOC = 80.0, arrivalBufferPercent = 10.0,
            prefer = { it.reliabilityScore }, objective = RouteObjective.RELIABLE,
        ).map { sequence -> sequence.map { it.id } }
        assertEquals(run(), run())
    }
}
