package com.evfastroute.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChargerScoringTest {

    private fun charger(
        connectors: List<ConnectorType> = listOf(ConnectorType.CCS),
        maxKw: Int = 150,
        details: List<ChargerConnector> = emptyList(),
        network: String = "Test",
    ) = Charger(
        id = "1", name = "x", network = network, latitude = 0.0, longitude = 0.0,
        connectorTypes = connectors, maxKw = maxKw, numberOfStalls = 4, connectorDetails = details,
    )

    @Test
    fun networkMatchesIsFuzzyAndCaseInsensitive() {
        assertTrue(ChargerScoring.networkMatches("Tesla Supercharger", setOf("tesla")))
        assertTrue(ChargerScoring.networkMatches("Electrify Canada", setOf("Electrify")))
        assertFalse(ChargerScoring.networkMatches("Petro-Canada", setOf("tesla")))
        assertFalse(ChargerScoring.networkMatches("Anything", setOf("")))   // empty pref never matches
    }

    @Test
    fun compatiblePowerPrefersDetailedThenFallsBackThenNull() {
        val basic = charger(connectors = listOf(ConnectorType.CCS), maxKw = 150)
        assertEquals(150, basic.compatiblePower(listOf(ConnectorType.CCS)))
        assertNull(basic.compatiblePower(listOf(ConnectorType.CHADEMO)))

        val detailed = charger(
            connectors = listOf(ConnectorType.CCS, ConnectorType.CHADEMO), maxKw = 150,
            details = listOf(ChargerConnector(ConnectorType.CHADEMO, 50)),
        )
        assertEquals(50, detailed.compatiblePower(listOf(ConnectorType.CHADEMO)))
    }

    @Test
    fun availabilityRatioIsNullWhenUnknown() {
        assertNull(charger().availabilityRatio)
        val live = charger().copy(availableStalls = 2, numberOfStalls = 4)
        assertEquals(0.5, live.availabilityRatio)
    }

    @Test
    fun sequenceKeyJoinsIdsStably() {
        assertEquals("a|b|c", ChargerScoring.sequenceKey(listOf("a", "b", "c")))
    }
}
