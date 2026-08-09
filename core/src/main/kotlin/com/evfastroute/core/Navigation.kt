package com.evfastroute.core

// Navigation handoff. EV FastRoute plans the trip and the charging stops; a dedicated navigator
// (Google Maps, Waze, or the system default maps app) does the turn-by-turn — voice, live traffic,
// lane guidance — that a map SDK doesn't expose. Faithful port of the iOS NavigationHandoff URL
// builders. Only the actual Intent firing is Android-specific and lives in :app; everything here
// is pure and unit-tested so link behaviour is provably identical to iOS.

/** Which external navigator to hand driving guidance to. */
enum class NavigationApp(val serialized: String, val displayName: String) {
    GOOGLE_MAPS("google", "Google Maps"),
    WAZE("waze", "Waze"),
    DEFAULT("default", "Default maps app"); // geo: URI → the system app chooser

    companion object {
        fun fromSerialized(value: String?): NavigationApp =
            entries.firstOrNull { it.serialized == value } ?: GOOGLE_MAPS
    }
}

/** A point handed to a navigator: a coordinate plus a display name. */
data class NavigationPoint(
    val latitude: Double,
    val longitude: Double,
    val name: String,
    val kind: Kind = Kind.VISIT,
) {
    enum class Kind { CHARGING, VISIT, DESTINATION }
}

/** A resolved handoff: the deep link to open, plus an optional note when only part of the trip
 * could be carried (Waze / default / oversized Google links take one destination at a time). */
data class NavigationHandoffPlan(val url: String, val note: String?)

object NavigationLinks {

    /** Google documents up to three waypoints for mobile-browser Maps URLs; universal links can
     * fall back to the browser when Google Maps isn't installed, so three is the only
     * cross-device-safe ceiling. */
    const val MAX_GOOGLE_UNIVERSAL_WAYPOINTS = 3

    /**
     * Resolves a handoff for the chosen app. Google gets the full route when it fits the waypoint
     * cap; otherwise (and always for Waze / the default app, which can't take waypoints in a deep
     * link) it routes to the next actionable point and returns a note telling the driver to come
     * back to EV FastRoute to continue the EV itinerary. Mirrors iOS `NavigationHandoff.open`.
     */
    fun handoff(
        app: NavigationApp,
        origin: NavigationPoint?,
        stops: List<NavigationPoint>,
        destination: NavigationPoint,
    ): NavigationHandoffPlan? = when (app) {
        NavigationApp.GOOGLE_MAPS -> {
            if (stops.size <= MAX_GOOGLE_UNIVERSAL_WAYPOINTS) {
                googleDirectionsUrl(origin, stops, destination)?.let { NavigationHandoffPlan(it, null) }
            } else {
                val next = stops.first()
                googleDirectionsUrl(null, emptyList(), next)?.let {
                    NavigationHandoffPlan(
                        it,
                        "This trip has more stops than a Google Maps link can safely carry on every " +
                            "device. Google Maps was opened to ${next.name}, your next stop. Return to " +
                            "EV FastRoute there to continue the complete EV itinerary.",
                    )
                }
            }
        }
        NavigationApp.WAZE -> {
            val next = stops.firstOrNull() ?: destination
            wazeDirectionsUrl(next)?.let { NavigationHandoffPlan(it, nextStopNote(app, stops, next)) }
        }
        NavigationApp.DEFAULT -> {
            val next = stops.firstOrNull() ?: destination
            NavigationHandoffPlan(geoUrl(next), nextStopNote(app, stops, next))
        }
    }

    /** User-facing button wording that reflects each provider's real deep-link capability. */
    fun actionLabel(app: NavigationApp, intermediateStopCount: Int): String {
        if (app == NavigationApp.GOOGLE_MAPS && intermediateStopCount <= MAX_GOOGLE_UNIVERSAL_WAYPOINTS) {
            return "Full trip in ${app.displayName}"
        }
        if (intermediateStopCount > 0) return "Next stop in ${app.displayName}"
        return "Navigate in ${app.displayName}"
    }

    /**
     * Official cross-platform Google Maps directions URL. Exact coordinates take priority over
     * labels: Open Charge Map doesn't provide Google Place IDs, and mixing an unverified name with
     * coordinates can silently drop or relocate waypoints. Returns null past the waypoint cap.
     */
    fun googleDirectionsUrl(
        origin: NavigationPoint?,
        stops: List<NavigationPoint>,
        destination: NavigationPoint,
    ): String? {
        if (stops.size > MAX_GOOGLE_UNIVERSAL_WAYPOINTS) return null
        val params = mutableListOf("api" to "1")
        origin?.let { params.add("origin" to coordinateValue(it)) }
        params.add("destination" to coordinateValue(destination))
        if (stops.isNotEmpty()) params.add("waypoints" to stops.joinToString("|") { coordinateValue(it) })
        params.add("travelmode" to "driving")
        params.add("dir_action" to "navigate")
        return encodedUrl("https://www.google.com/maps/dir/", params)
    }

    fun wazeDirectionsUrl(destination: NavigationPoint): String? {
        val params = mutableListOf("ll" to coordinateValue(destination))
        val name = destination.name.trim()
        if (name.isNotEmpty()) params.add("q" to name)
        params.add("navigate" to "yes")
        params.add("utm_source" to "EVFastRoute")
        return encodedUrl("https://waze.com/ul", params)
    }

    /** `geo:` URI for the system default maps app (opens the Android app chooser). */
    fun geoUrl(destination: NavigationPoint): String {
        val coord = coordinateValue(destination)
        val name = destination.name.trim()
        val query = if (name.isEmpty()) coord else "$coord(${percentEncode(name)})"
        return "geo:${destination.latitude},${destination.longitude}?q=$query"
    }

    private fun nextStopNote(app: NavigationApp, stops: List<NavigationPoint>, next: NavigationPoint): String? =
        if (stops.isEmpty()) null
        else "${app.displayName} was opened to ${next.name}, your next stop. Return to EV FastRoute " +
            "there to continue the complete EV itinerary."

    private fun coordinateValue(point: NavigationPoint): String = "${point.latitude},${point.longitude}"

    private fun encodedUrl(base: String, params: List<Pair<String, String>>): String {
        val query = params.joinToString("&") { (key, value) -> "$key=${percentEncode(value)}" }
        return "$base?$query"
    }

    /** Percent-encodes with the same unreserved set as iOS (`alphanumerics + "-._~"`), so commas,
     * pipes and spaces are escaped identically on both platforms. */
    private fun percentEncode(value: String): String {
        val unreserved = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        return buildString {
            value.forEach { ch ->
                if (ch in unreserved) {
                    append(ch)
                } else {
                    ch.toString().toByteArray(Charsets.UTF_8).forEach { b -> append("%%%02X".format(b.toInt() and 0xFF)) }
                }
            }
        }
    }
}
