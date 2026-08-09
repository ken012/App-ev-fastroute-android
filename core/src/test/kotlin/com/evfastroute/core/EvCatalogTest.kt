package com.evfastroute.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Locks the ported EV catalog behaviour (search, region connector mapping, vehicle derivation). */
class EvCatalogTest {

    @Test
    fun catalogIsNonEmptyAndCoversMultipleMakes() {
        assertTrue(EvCatalog.presets.isNotEmpty())
        assertTrue(EvCatalog.makeCount >= 10, "expected several makes, got ${EvCatalog.makeCount}")
    }

    @Test
    fun sortedByMakeThenModelThenNewestYear() {
        val presets = EvCatalog.presets
        for (i in 1 until presets.size) {
            val a = presets[i - 1]
            val b = presets[i]
            val makeCmp = a.make.lowercase().compareTo(b.make.lowercase())
            assertTrue(makeCmp <= 0, "makes out of order at $i: ${a.make} then ${b.make}")
            if (makeCmp == 0) {
                val modelCmp = a.model.lowercase().compareTo(b.model.lowercase())
                assertTrue(modelCmp <= 0, "models out of order at $i")
                if (modelCmp == 0) assertTrue(a.year >= b.year, "years should be newest-first at $i")
            }
        }
    }

    @Test
    fun identifiersAreUnique() {
        val ids = EvCatalog.presets.map { it.catalogIdentifier }
        assertEquals(ids.size, ids.toSet().size, "duplicate catalog identifiers present")
    }

    @Test
    fun searchIsTokenAndAndFindsModel3() {
        val results = EvCatalog.search("tesla model 3")
        assertTrue(results.any { it.make == "Tesla" && it.model.startsWith("Model 3") })
        // AND semantics: an unrelated extra token yields nothing.
        assertTrue(EvCatalog.search("tesla ioniq").isEmpty())
    }

    @Test
    fun searchIsCaseAndPunctuationInsensitiveAndMatchesCompact() {
        assertTrue(EvCatalog.search("MODELY").any { it.model == "Model Y Long Range" })
        assertTrue(EvCatalog.search("id.4").any { it.model == "ID.4" })
    }

    @Test
    fun emptyQueryReturnsEverything() {
        assertEquals(EvCatalog.presets.size, EvCatalog.search("   ").size)
    }

    @Test
    fun presetLookupByIdentifierRoundTrips() {
        val first = EvCatalog.presets.first()
        assertEquals(first, EvCatalog.preset(withIdentifier = first.catalogIdentifier))
        assertNull(EvCatalog.preset(withIdentifier = "does-not-exist"))
        assertNull(EvCatalog.preset(withIdentifier = null))
    }

    @Test
    fun europeanRegionMapsNacsAndCcsToCcs2WithoutDuplicates() {
        val machE = EvCatalog.presets.first { it.model == "Mustang Mach-E" } // CCS + NACS
        assertEquals(listOf(ConnectorType.CCS2), machE.connectorTypes(european = true))
        // Non-European collapses CCS2 back to CCS.
        val bmw = EvCatalog.presets.first { it.model == "i4 eDrive40" } // CCS2
        assertEquals(listOf(ConnectorType.CCS), bmw.connectorTypes(european = false))
    }

    @Test
    fun toVehicleCarriesPhysicsAndRegionConnectors() {
        val tesla = EvCatalog.presets.first { it.model == "Model 3 Long Range" }
        val vehicle = tesla.toVehicle(european = false)
        assertEquals(75.0, vehicle.batteryCapacityKwh)
        assertEquals(0.15, vehicle.efficiencyKwhPerKm)
        assertEquals(250, vehicle.maxDcChargingKw)
        // NACS in North America stays NACS.
        assertEquals(listOf(ConnectorType.NACS), vehicle.connectorTypes)
        // In Europe the same Tesla is charged as CCS2.
        assertEquals(listOf(ConnectorType.CCS2), tesla.toVehicle(european = true).connectorTypes)
    }

    @Test
    fun kiaSupplementIsPresentWithRealSpecs() {
        val ev9 = EvCatalog.preset(withIdentifier = "kia-media:ev9:2024:light-long-range-rwd")
        assertNotNull(ev9)
        assertEquals("Kia", ev9.make)
        assertEquals(99.8, ev9.batteryCapacityKwh)
    }

    @Test
    fun defaultIsAModel3() {
        assertEquals("Tesla", EvCatalog.default.make)
        assertTrue(EvCatalog.default.model.startsWith("Model 3"))
    }
}
