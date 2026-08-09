package com.evfastroute.core

import kotlinx.serialization.Serializable

// Navigation handoff. EV FastRoute plans the trip and the charging stops; a dedicated navigator
// (Google Maps, Waze, or the system default maps app) does the turn-by-turn — voice, live traffic,
// lane guidance — that a map SDK doesn't expose. Faithful port of the iOS NavigationHandoff URL
// builders. Only the actual Intent firing is Android-specific and lives in :app; everything here
// is pure and unit-tested so link behaviour is provably identical to iOS.

/** Which external navigator to hand driving guidance to. */
@Serializable
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
@Serializable
data class NavigationPoint(
    val latitude: Double,
    val longitude: Double,
    val name: String,
    val kind: Kind = Kind.VISIT,
) {
    @Serializable
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

    /** Percent-encodes reserved characters (commas, pipes, spaces, `&`, `=`, …) so the query is
     * always well-formed. Uses an ASCII unreserved set; unlike iOS (whose `alphanumerics` spares
     * all Unicode letters), non-ASCII characters in a place name are UTF-8 percent-encoded here —
     * which is the safer, still-valid form. Coordinates are ASCII, so routing values are identical. */
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

/**
 * Durable progress for a multi-stop handoff to navigators that accept only one destination per
 * deep link (Waze, the default maps app, and oversized Google itineraries). The full itinerary
 * stays in EV FastRoute; each point is handed off one at a time and the driver confirms arrival to
 * advance. Faithful port of the iOS ExternalNavigationSession — immutable here (each transition
 * returns a new value), with epoch-millis timestamps so the logic is pure and testable. It never
 * auto-advances: [shouldSuggestArrival] only decides whether to *offer* a confirm prompt.
 */
@Serializable
data class NavigationSession(
    val itinerary: List<NavigationPoint>, // intermediate stops followed by the destination
    val app: NavigationApp,
    val startedAtMillis: Long,
    val nextIndex: Int = 0,
    val lastHandoffAtMillis: Long? = null,
    val arrivalPromptedIndex: Int? = null,
) {
    val currentPoint: NavigationPoint? get() = itinerary.getOrNull(nextIndex)
    val isComplete: Boolean get() = nextIndex >= itinerary.size
    val completedPointCount: Int get() = minOf(nextIndex, itinerary.size)
    val totalPointCount: Int get() = itinerary.size
    val remainingPointCount: Int get() = maxOf(0, itinerary.size - nextIndex)

    fun withApp(app: NavigationApp): NavigationSession = copy(app = app)

    fun recordHandoff(atMillis: Long): NavigationSession =
        copy(lastHandoffAtMillis = atMillis, arrivalPromptedIndex = null)

    fun recordArrivalPrompt(): NavigationSession =
        if (currentPoint == null) this else copy(arrivalPromptedIndex = nextIndex)

    /** Advances to the next point after explicit driver confirmation. No-op once complete. */
    fun markCurrentPointComplete(): NavigationSession =
        if (currentPoint == null) this
        else copy(nextIndex = nextIndex + 1, arrivalPromptedIndex = null, lastHandoffAtMillis = null)

    /**
     * Conservative proximity hint used only to *offer* a confirmation prompt — never to advance.
     * Rejects stale/inaccurate samples and waits long enough after handoff to avoid prompting while
     * the external app is still launching. Mirrors iOS shouldSuggestArrival.
     */
    fun shouldSuggestArrival(
        userLatitude: Double,
        userLongitude: Double,
        horizontalAccuracyMeters: Double?,
        sampleAtMillis: Long?,
        nowMillis: Long,
    ): Boolean {
        val point = currentPoint ?: return false
        if (arrivalPromptedIndex == nextIndex) return false
        val handoff = lastHandoffAtMillis ?: return false
        val sample = sampleAtMillis ?: return false
        if (sample < handoff) return false
        if (nowMillis - handoff < MIN_HANDOFF_DURATION_MILLIS) return false
        val accuracy = horizontalAccuracyMeters ?: return false
        if (accuracy < 0 || accuracy > MAX_ARRIVAL_ACCURACY_METERS) return false
        val distance = Geometry.haversineMeters(userLatitude, userLongitude, point.latitude, point.longitude)
        return distance <= ARRIVAL_RADIUS_METERS
    }

    companion object {
        const val ARRIVAL_RADIUS_METERS = 200.0
        const val MAX_ARRIVAL_ACCURACY_METERS = 100.0
        const val MIN_HANDOFF_DURATION_MILLIS = 20_000L

        /** Builds a session over the intermediate [stops] followed by [destination]. */
        fun create(
            stops: List<NavigationPoint>,
            destination: NavigationPoint,
            app: NavigationApp,
            startedAtMillis: Long,
        ): NavigationSession = NavigationSession(itinerary = stops + destination, app = app, startedAtMillis = startedAtMillis)
    }
}

/**
 * The intermediate stops (charging + the driver's own visits) in travel order, reconstructed from
 * the route's segment indices — the input to a sequential [NavigationSession]. Mirrors iOS
 * NavigationHandoff.orderedStops (waypoints then chargers, ordered by segment then insertion).
 */
fun RouteOption.orderedNavigationPoints(): List<NavigationPoint> {
    data class Indexed(val segment: Int, val order: Int, val point: NavigationPoint)

    val items = mutableListOf<Indexed>()
    userWaypoints.forEachIndexed { i, wp ->
        items.add(
            Indexed(
                userWaypointSegmentIndices.getOrNull(i) ?: Int.MAX_VALUE, i,
                NavigationPoint(wp.latitude, wp.longitude, wp.placeName, NavigationPoint.Kind.VISIT),
            ),
        )
    }
    val offset = userWaypoints.size
    chargingStops.forEachIndexed { i, stop ->
        items.add(
            Indexed(
                stopSegmentIndices.getOrNull(i) ?: Int.MAX_VALUE, offset + i,
                NavigationPoint(stop.latitude, stop.longitude, stop.name, NavigationPoint.Kind.CHARGING),
            ),
        )
    }
    return items.sortedWith(compareBy({ it.segment }, { it.order })).map { it.point }
}
