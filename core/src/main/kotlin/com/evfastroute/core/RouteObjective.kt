package com.evfastroute.core

// The outcome the driver wants the planner to optimize. Port of iOS RouteObjective.swift —
// kept as data (not a display string) so refreshes/reroutes preserve intent even when identical
// routes are deduplicated in the UI. `serialized` matches the Swift raw values for persistence.

enum class RouteObjective {
    FASTEST,
    RELIABLE,
    LOWEST_COST,
    FEWEST_STOPS,
    DIRECT,
    VERIFIED;

    /** Matches the Swift enum raw value (for cross-platform-consistent persistence). */
    val serialized: String
        get() = when (this) {
            FASTEST -> "fastest"
            RELIABLE -> "reliable"
            LOWEST_COST -> "lowestCost"
            FEWEST_STOPS -> "fewestStops"
            DIRECT -> "direct"
            VERIFIED -> "verified"
        }

    val title: String
        get() = when (this) {
            FASTEST -> "Fastest arrival"
            RELIABLE -> "Highest-confidence stations"
            LOWEST_COST -> "Lowest estimated charging cost"
            FEWEST_STOPS -> "Fewest charging stops"
            DIRECT -> "Direct — no charging needed"
            VERIFIED -> "Verified corridor route"
        }

    val mode: String
        get() = when (this) {
            FASTEST -> "Fastest"
            RELIABLE -> "Reliable"
            LOWEST_COST -> "Lowest estimate"
            FEWEST_STOPS -> "Fewest stops"
            DIRECT -> "Direct"
            VERIFIED -> "Verified"
        }

    val explanation: String
        get() = when (this) {
            FASTEST ->
                "Lowest verified driving and charging time. More short charging stops can be faster than one long stop."
            RELIABLE ->
                "Prioritizes stations with stronger operational-status and data-confidence signals."
            LOWEST_COST ->
                "Prioritizes the lowest estimated charging cost, then arrival time."
            FEWEST_STOPS ->
                "Minimizes charging sessions first. Individual stops may be longer, including charging beyond 80%."
            DIRECT ->
                "No charging stop is needed to arrive with the selected battery buffer."
            VERIFIED ->
                "A conservative fallback whose complete road sequence was verified by the routing service."
        }

    companion object {
        /** User-selectable objectives, in display order. Mirrors Swift `plannerCases`. */
        val plannerCases: List<RouteObjective> = listOf(FASTEST, RELIABLE, LOWEST_COST, FEWEST_STOPS)

        /** Back-compat with routes persisted using the legacy `mode` string. */
        fun fromLegacyMode(mode: String): RouteObjective = when (mode.lowercase()) {
            "fastest" -> FASTEST
            "reliable" -> RELIABLE
            "lowest estimate", "lowest cost" -> LOWEST_COST
            "fewest stops" -> FEWEST_STOPS
            "direct" -> DIRECT
            else -> VERIFIED
        }

        fun fromSerialized(value: String): RouteObjective =
            entries.firstOrNull { it.serialized == value } ?: VERIFIED
    }
}
