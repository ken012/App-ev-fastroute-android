package com.evfastroute.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenChargeMapTest {

    private val sample = """
        [
          {
            "ID": 12345,
            "AddressInfo": {
              "Title": "Supercharger Kingston",
              "Latitude": 44.23, "Longitude": -76.48,
              "Country": { "ISOCode": "CA" }
            },
            "OperatorInfo": { "Title": "Tesla" },
            "DataProvider": {
              "Title": "Open Charge Map Contributors",
              "WebsiteURL": "https://openchargemap.org",
              "License": "CC BY 4.0",
              "IsOpenDataLicensed": true
            },
            "StatusType": { "IsOperational": true },
            "NumberOfPoints": 8,
            "UsageCost": "$0.45/kWh",
            "Connections": [
              { "ConnectionType": { "Title": "CCS (Type 2)" }, "PowerKW": 250.0, "Quantity": 8 }
            ]
          }
        ]
    """.trimIndent()

    @Test
    fun parsesOcmPoiIntoCharger() {
        val chargers = OpenChargeMap.parse(sample)
        assertEquals(1, chargers.size)
        val c = chargers[0]
        assertEquals("ocm-12345", c.id)
        assertEquals("Supercharger Kingston", c.name)
        assertEquals("Tesla", c.network)
        assertEquals(listOf(ConnectorType.CCS2), c.connectorTypes)
        assertEquals(250, c.maxKw)
        assertEquals(8, c.numberOfStalls)
        assertEquals(ChargerStatus.AVAILABLE, c.status)
        assertEquals(95.0, c.reliabilityScore, 1e-9)
        assertEquals("CA", c.region)
        assertEquals(0.45, c.pricePerKwh!!, 1e-9)
        assertEquals("CAD", c.priceCurrencyCode)
        assertEquals("Open Charge Map Contributors", c.dataProviderTitle)
        assertEquals("CC BY 4.0", c.dataProviderLicense)
        assertEquals("https://openchargemap.org", c.dataProviderWebsiteUrl)
    }

    @Test
    fun connectorTitleMappingMatchesIOS() {
        assertEquals(ConnectorType.CCS2, OpenChargeMap.connectorFromTitle("CCS (Type 2)"))
        assertEquals(ConnectorType.CCS, OpenChargeMap.connectorFromTitle("CCS (Type 1)"))
        assertEquals(ConnectorType.CHADEMO, OpenChargeMap.connectorFromTitle("CHAdeMO"))
        assertEquals(ConnectorType.NACS, OpenChargeMap.connectorFromTitle("Tesla (NACS)"))
        assertEquals(ConnectorType.TYPE2, OpenChargeMap.connectorFromTitle("Type 2 (Mennekes)"))
        assertNull(OpenChargeMap.connectorFromTitle("Domestic plug"))
        assertNull(OpenChargeMap.connectorFromTitle(null))
    }

    @Test
    fun poiWithNoRecognizedConnectorIsSkipped() {
        val body = """[{"ID":1,"AddressInfo":{"Latitude":45.0,"Longitude":-74.0},"Connections":[{"ConnectionType":{"Title":"Domestic plug"}}]}]"""
        assertTrue(OpenChargeMap.parse(body).isEmpty())
    }

    @Test
    fun missingPowerAndInvalidCoordinatesAreRejected() {
        val missingPower = """[{"ID":1,"AddressInfo":{"Latitude":45.0,"Longitude":-74.0},"Connections":[{"ConnectionType":{"Title":"CCS"}}]}]"""
        val invalidCoordinate = """[{"ID":1,"AddressInfo":{"Latitude":145.0,"Longitude":-74.0},"Connections":[{"ConnectionType":{"Title":"CCS"},"PowerKW":100}]}]"""
        assertTrue(OpenChargeMap.parse(missingPower).isEmpty())
        assertTrue(OpenChargeMap.parse(invalidCoordinate).isEmpty())
    }

    @Test
    fun unknownStatusIsLimitedAndUnknownPriceStaysUnknown() {
        val body = """[{"ID":2,"AddressInfo":{"Latitude":45.0,"Longitude":-74.0,"Country":{"ISOCode":"CA"}},"UsageCost":"$5 parking per hour","Connections":[{"ConnectionType":{"Title":"CCS"},"PowerKW":100}]}]"""
        val charger = OpenChargeMap.parse(body).single()
        assertEquals(ChargerStatus.LIMITED, charger.status)
        assertNull(charger.pricePerKwh)
        assertNull(charger.priceCurrencyCode)
    }

    @Test
    fun priceParserRequiresEnergyUnitsAndPreservesCurrency() {
        assertEquals(OpenChargeMap.ParsedPrice(0.39, "USD"), OpenChargeMap.parsePricePerKwh("USD 0.39 per kWh", "US"))
        assertEquals(OpenChargeMap.ParsedPrice(0.45, "CAD"), OpenChargeMap.parsePricePerKwh("$2 session plus $0.45/kWh", "CA"))
        assertEquals(OpenChargeMap.ParsedPrice(0.0, "CAD"), OpenChargeMap.parsePricePerKwh("Free", "CA"))
        assertNull(OpenChargeMap.parsePricePerKwh("$2 connection fee", "CA"))
    }

    @Test
    fun malformedPayloadIsDistinctFromAValidEmptyResponse() {
        assertNull(OpenChargeMap.parseOrNull("not-json"))
        assertEquals(emptyList(), OpenChargeMap.parseOrNull("[]"))
    }
}
