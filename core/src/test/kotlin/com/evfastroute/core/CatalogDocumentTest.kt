package com.evfastroute.core

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Locks the bundled OpenEV catalog document parsing + promotion (schema, connector tokens, size gate). */
class CatalogDocumentTest {

    @AfterTest
    fun cleanup() = EvCatalog.resetForTest() // never leak a loaded catalog into other tests

    private val twoCarDoc = """
        {"schemaVersion":1,"vehicles":[
          {"catalogIdentifier":"x:bolt","make":"Chevrolet","model":"Bolt","year":2023,
           "batteryCapacityKwh":65.0,"maxDcChargingKw":55,"efficiencyKwhPerKm":0.17,
           "connectorTypes":["CCS1"],"ratedRangeKm":417.0,"rangeStandard":"EPA",
           "sourceName":"OpenEV","sourceURL":"http://example.com"},
          {"make":"Tesla","model":"Model 3","year":2024,
           "batteryCapacityKwh":60.0,"maxDcChargingKw":250,"efficiencyKwhPerKm":0.15,
           "connectorTypes":["NACS/Tesla"]}
        ]}
    """.trimIndent()

    @Test
    fun parsesFieldsAndMapsConnectorTokens() {
        val presets = EvCatalog.parseDocument(twoCarDoc)!!
        assertEquals(2, presets.size)
        val bolt = presets.first { it.make == "Chevrolet" }
        assertEquals(listOf(ConnectorType.CCS), bolt.connectorTypes) // CCS1 → CCS
        assertEquals(417.0, bolt.ratedRangeKm)
        assertEquals("x:bolt", bolt.catalogIdentifier)
        val tesla = presets.first { it.make == "Tesla" }
        assertEquals(listOf(ConnectorType.NACS), tesla.connectorTypes) // NACS/Tesla → NACS
        // A vehicle without an explicit identifier gets a derived one.
        assertEquals(EvPreset.defaultIdentifier("Tesla", "Model 3", 2024), tesla.catalogIdentifier)
    }

    @Test
    fun rejectsUnknownSchemaAndGarbage() {
        assertNull(EvCatalog.parseDocument("""{"schemaVersion":2,"vehicles":[]}"""))
        assertNull(EvCatalog.parseDocument("not json at all"))
    }

    @Test
    fun tooSmallDocumentIsNotPromoted() {
        assertFalse(EvCatalog.loadBundledCatalog(twoCarDoc)) // default gate is 700
        assertEquals("Tesla", EvCatalog.default.make)        // built-in default still active
    }

    @Test
    fun largeEnoughDocumentReplacesCatalogAndKeepsSupplements() {
        assertTrue(EvCatalog.loadBundledCatalog(twoCarDoc, minimumVehicles = 2))
        assertTrue(EvCatalog.presets.any { it.make == "Chevrolet" && it.model == "Bolt" })
        assertTrue(EvCatalog.presets.any { it.make == "Kia" }) // manufacturer supplements merged in
    }
}
