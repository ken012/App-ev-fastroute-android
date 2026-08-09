package com.evfastroute.core

import kotlin.math.ceil
import kotlin.math.roundToInt

// MARK: - Multi-stop charge planning (pure, testable)
//
// Direct Kotlin port of the iOS ChargePlanner (Swift). Kept behavior-identical and covered by
// the same test cases so both platforms plan charging to the same standard. SOC-only (no
// geometry); the routing service maps each leg's along-route distance to a real charger. All
// figures are estimates (constant efficiency, average charge power, generic DC taper).

/** Cumulative-distance charging stop. Mirrors iOS `PlannedLeg`. */
data class PlannedLeg(
    val atKm: Double,       // cumulative distance from start where this stop occurs
    val arrivalSOC: Int,    // % state of charge on arrival at the charger
    val targetSOC: Int,     // % to charge to before departing
)

object ChargePlanner {

    /**
     * Plans charging legs. Returns [] when the car can reach the destination (with buffer)
     * on its current charge.
     */
    fun planLegs(
        distanceKm: Double,
        capacityKwh: Double,
        efficiencyKwhPerKm: Double,
        currentSOC: Double,
        arrivalBufferPercent: Double,
        maxChargeSOC: Double = 80.0,
        minSOC: Double = 10.0,
        maxStops: Int = 8,
    ): List<PlannedLeg> {
        val capacity = maxOf(1.0, capacityKwh)
        val socPerKm = (efficiencyKwhPerKm / capacity) * 100.0
        if (socPerKm <= 0.0 || distanceKm <= 0.0 || efficiencyKwhPerKm <= 0.0) return emptyList()

        val legs = mutableListOf<PlannedLeg>()
        var soc = currentSOC
        var traveled = 0.0

        while (legs.size < maxStops) {
            val remainingKm = distanceKm - traveled
            val socToFinish = remainingKm * socPerKm + arrivalBufferPercent
            if (soc >= socToFinish) break                          // can finish with buffer intact

            val kmToMin = maxOf(0.0, (soc - minSOC) / socPerKm)     // how far before hitting minSOC
            val legKm = minOf(kmToMin, remainingKm)
            val stopAt = traveled + legKm

            if (stopAt >= distanceKm - 0.5) break                  // essentially at destination
            if (legKm < 1.0 && legs.isNotEmpty()) break            // no meaningful progress — stop looping

            val arrivalSOC = maxOf(minSOC.roundToInt(), (soc - legKm * socPerKm).roundToInt())
            val remainingAfter = distanceKm - stopAt
            val socNeededAfter = remainingAfter * socPerKm + arrivalBufferPercent
            val target = minOf(maxChargeSOC, maxOf(arrivalSOC.toDouble(), socNeededAfter))

            legs.add(PlannedLeg(atKm = stopAt, arrivalSOC = arrivalSOC, targetSOC = target.roundToInt()))
            traveled = stopAt
            soc = target
        }
        return legs
    }

    /** State of charge on arrival at the destination given the last leg's departure SOC. */
    fun arrivalSOC(
        distanceKm: Double,
        capacityKwh: Double,
        efficiencyKwhPerKm: Double,
        currentSOC: Double,
        legs: List<PlannedLeg>,
    ): Int {
        val capacity = maxOf(1.0, capacityKwh)
        val socPerKm = (efficiencyKwhPerKm / capacity) * 100.0
        val last = legs.lastOrNull()
        if (last != null) {
            val remaining = maxOf(0.0, distanceKm - last.atKm)
            return (last.targetSOC.toDouble() - remaining * socPerKm).roundToInt().coerceIn(0, 100)
        }
        return (currentSOC - distanceKm * socPerKm).roundToInt().coerceIn(0, 100)
    }

    /**
     * Charging time (minutes) using a conservative generic DC fast-charge curve.
     *
     * Nameplate charging power is normally available only in the lower/middle part of the
     * battery. Treating 80→95% like 20→35% makes long-session routes look implausibly fast,
     * so energy is integrated through SOC bands with progressively stronger taper. Vehicle-
     * specific curves can replace these defaults when that data is available in the catalog.
     */
    fun chargeMinutes(
        fromSOC: Int,
        toSOC: Int,
        capacityKwh: Double,
        effectiveKw: Double,
        sessionOverheadMinutes: Double = 3.0,
    ): Int {
        val start = fromSOC.coerceIn(0, 100)
        val end = toSOC.coerceIn(start, 100)
        if (end <= start || capacityKwh <= 0.0 || effectiveKw <= 0.0) return 0

        // Upper bound is exclusive. Fractions represent typical power relative to the lower-SOC
        // peak and intentionally become conservative where nearly every EV tapers aggressively.
        val bands = listOf(
            Band(0, 50, 1.00),
            Band(50, 70, 0.85),
            Band(70, 80, 0.70),
            Band(80, 90, 0.45),
            Band(90, 100, 0.25),
        )
        var activeChargingMinutes = 0.0
        for (band in bands) {
            val overlapStart = maxOf(start, band.lower)
            val overlapEnd = minOf(end, band.upper)
            if (overlapEnd <= overlapStart) continue
            val energyKwh = capacityKwh * (overlapEnd - overlapStart) / 100.0
            activeChargingMinutes += energyKwh / (effectiveKw * band.powerFraction) * 60.0
        }

        val total = activeChargingMinutes + maxOf(0.0, sessionOverheadMinutes)
        return maxOf(5, ceil(total).toInt())
    }

    /** kWh added in a session (for cost). */
    fun energyAdded(fromSOC: Int, toSOC: Int, capacityKwh: Double): Double =
        capacityKwh * maxOf(0, toSOC - fromSOC) / 100.0

    private data class Band(val lower: Int, val upper: Int, val powerFraction: Double)
}
