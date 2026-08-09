package com.evfastroute.android

import android.app.Application
import com.evfastroute.core.EvCatalog

// Loads the bundled 789-car OpenEV catalog once at process start, before any screen (and thus any
// ViewModel) reads EvCatalog.default. If the asset is missing or unreadable, EvCatalog keeps its
// built-in starter set, so the app still works.
class EvApp : Application() {
    override fun onCreate() {
        super.onCreate()
        runCatching { assets.open("ev_catalog.json").bufferedReader().use { it.readText() } }
            .getOrNull()
            ?.let { EvCatalog.loadBundledCatalog(it) }
    }
}
