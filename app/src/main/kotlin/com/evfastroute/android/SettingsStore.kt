package com.evfastroute.android

import android.content.Context
import com.evfastroute.core.NavigationApp
import com.evfastroute.core.Region
import java.util.Locale

// On-device preferences (SharedPreferences), the Android analog of the iOS SettingsService keys
// this app uses: region, distance units, and the preferred navigation app. Values persist across
// launches; units fall back to the region default until the user picks explicitly.
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("evfr_settings", Context.MODE_PRIVATE)

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

    private companion object {
        const val KEY_REGION = "evfr_region"
        const val KEY_USES_MILES = "evfr_uses_miles"
        const val KEY_NAV = "evfr_preferred_navigation_app"
    }
}
