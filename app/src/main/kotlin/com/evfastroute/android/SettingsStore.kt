package com.evfastroute.android

import android.content.Context
import com.evfastroute.core.NavigationApp
import com.evfastroute.core.NavigationSession
import com.evfastroute.core.Region
import java.util.Locale
import kotlinx.serialization.json.Json

// On-device preferences (SharedPreferences), the Android analog of the iOS SettingsService keys
// this app uses: region, distance units, and the preferred navigation app. Values persist across
// launches; units fall back to the region default until the user picks explicitly.
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("evfr_settings", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    var region: Region
        get() = Region.from(prefs.getString(KEY_REGION, null) ?: Locale.getDefault().country)
        set(value) = prefs.edit().putString(KEY_REGION, value.code).apply()

    /** Explicit user choice if set, otherwise the region's conventional default. */
    var usesMiles: Boolean
        get() = if (prefs.contains(KEY_USES_MILES)) prefs.getBoolean(KEY_USES_MILES, false) else region.usesImperialByDefault
        set(value) = prefs.edit().putBoolean(KEY_USES_MILES, value).apply()

    var preferredNav: NavigationApp
        get() = NavigationApp.fromSerialized(prefs.getString(KEY_NAV, null))
        set(value) = prefs.edit().putString(KEY_NAV, value.serialized).apply()

    /** An in-progress sequential handoff, restored across launches; null when none is active. */
    var navigationSession: NavigationSession?
        get() = prefs.getString(KEY_SESSION, null)?.let {
            runCatching { json.decodeFromString(NavigationSession.serializer(), it) }.getOrNull()
        }
        set(value) {
            if (value == null) {
                prefs.edit().remove(KEY_SESSION).apply()
            } else {
                prefs.edit().putString(KEY_SESSION, json.encodeToString(NavigationSession.serializer(), value)).apply()
            }
        }

    private companion object {
        const val KEY_REGION = "evfr_region"
        const val KEY_USES_MILES = "evfr_uses_miles"
        const val KEY_NAV = "evfr_preferred_navigation_app"
        const val KEY_SESSION = "evfr_external_navigation_session"
    }
}
