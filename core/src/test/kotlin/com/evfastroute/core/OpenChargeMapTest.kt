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
        assertEquals(98.0, c.reliabilityScore, 1e-9) // 78 +10 operational +6 (≥150) +4 (≥250)
        assertEquals("CA", c.region)
        assertEquals(0.45, c.pricePerKwh, 1e-9)
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
}
