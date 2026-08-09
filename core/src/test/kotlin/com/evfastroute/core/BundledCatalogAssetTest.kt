package com.evfastroute.core

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Guards the SHIPPED catalog asset itself (app/src/main/assets/ev_catalog.json), not just a
 * synthetic document: if it drops below the production gate, loses a required field, or bumps its
 * schema, the app silently falls back to the 22-car built-in set — this test makes that visible in CI.
 */
class BundledCatalogAssetTest {

    @AfterTest
    fun cleanup() = EvCatalog.resetForTest()

    private fun assetText(): String {
        // :core test working dir is the module dir; try the sibling app module and the repo root.
        val candidates = listOf(
            "../app/src/main/assets/ev_catalog.json",
            "app/src/main/assets/ev_catalog.json",
        )
        val file = candidates.map { File(it) }.firstOrNull { it.exists() }
        assertNotNull(file, "ev_catalog.json not found; tried ${candidates.joinToString()} from ${File(".").absolutePath}")
        return file.readText()
    }

    @Test
    fun shippedCatalogParsesMeetsProductionGateAndLoads() {
        val json = assetText()
        val parsed = EvCatalog.parseDocument(json)
        assertNotNull(parsed, "shipped ev_catalog.json failed to decode")
        assertTrue(parsed.size >= 700, "shipped catalog below the production gate: ${parsed.size}")
        // The default gate (700) must accept the real asset and promote it as the active catalog.
        assertTrue(EvCatalog.loadBundledCatalog(json), "loadBundledCatalog rejected the shipped asset")
        assertTrue(EvCatalog.presets.size >= 700)
        assertTrue(EvCatalog.presets.any { it.make == "Tesla" })
        assertTrue(EvCatalog.presets.any { it.make == "Hyundai" })
        // Every promoted preset must carry at least one usable connector for compatibility checks.
        assertTrue(EvCatalog.presets.all { it.connectorTypes.isNotEmpty() })
    }
}
