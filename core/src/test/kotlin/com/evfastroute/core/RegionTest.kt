package com.evfastroute.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Locks region-dependent facts (units, currency, connectors, detection) ported from iOS Region. */
class RegionTest {

    @Test
    fun detectsFromIsoCodeAndDefaultsToUs() {
        assertEquals(Region.DE, Region.from("de"))
        assertEquals(Region.GB, Region.from("GB"))
        assertEquals(Region.US, Region.from(null))
        assertEquals(Region.US, Region.from("zz"))
    }

    @Test
    fun supportsKnownCodes() {
        assertTrue(Region.supports("fr"))
        assertTrue(Region.supports(null))
        assertFalse(Region.supports("zz"))
    }

    @Test
    fun imperialOnlyForUsAndUk() {
        assertTrue(Region.US.usesImperialByDefault)
        assertTrue(Region.GB.usesImperialByDefault)
        assertFalse(Region.CA.usesImperialByDefault)
        assertFalse(Region.DE.usesImperialByDefault)
    }

    @Test
    fun europeanFlagMatchesIosGrouping() {
        assertFalse(Region.US.isEuropean)
        assertFalse(Region.CA.isEuropean)
        assertTrue(Region.DE.isEuropean)
        assertTrue(Region.GB.isEuropean)
    }

    @Test
    fun currencyAndConnectorsByRegion() {
        assertEquals("$", Region.US.currencySymbol)
        assertEquals("£", Region.GB.currencySymbol)
        assertEquals("€", Region.DE.currencySymbol)
        assertEquals(listOf(ConnectorType.CCS, ConnectorType.NACS), Region.US.defaultConnectors)
        assertEquals(listOf(ConnectorType.CCS2, ConnectorType.TYPE2), Region.FR.defaultConnectors)
        assertEquals(
            setOf("Tesla Supercharger", "Electrify America", "ChargePoint", "EVgo"),
            Region.CA.defaultNetworks,
        )
        assertEquals(
            setOf("Ionity", "Fastned", "Allego", "Tesla", "Shell Recharge"),
            Region.DE.defaultNetworks,
        )
    }

    @Test
    fun flagIsRegionalIndicatorPair() {
        assertEquals("🇺🇸", Region.US.flag) // 🇺🇸
    }

    @Test
    fun distanceFormatsInChosenUnits() {
        assertEquals("100 km", Units.formatDistance(100.0, usesMiles = false))
        assertEquals("62 mi", Units.formatDistance(100.0, usesMiles = true))
        assertEquals("km", Units.label(false))
        assertEquals("mi", Units.label(true))
    }

    @Test
    fun consumptionFormatsInChosenUnits() {
        assertEquals("20.0 kWh/100 km", Units.formatConsumption(20.0, usesMiles = false))
        assertEquals("32.2 kWh/100 mi", Units.formatConsumption(20.0, usesMiles = true))
    }
}
