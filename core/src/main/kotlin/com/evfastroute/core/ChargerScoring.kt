package com.evfastroute.core

// Pure charger scoring/identity helpers shared by the planner. Faithful ports of the iOS
// RouteOptimizationService.speedScore / networkMatches / sequenceKey.

object ChargerScoring {

    fun sequenceKey(ids: List<String>): String = ids.joinToString("|")

    /** Fuzzy, case-insensitive network membership. Empty preferences never match. */
    fun networkMatches(network: String, set: Set<String>): Boolean {
        val n = network.lowercase()
        return set.any { pref ->
            val p = pref.lowercase()
            p.isNotEmpty() && (n == p || n.contains(p) || p.contains(n))
        }
    }

    /** Time-objective charger preference (higher is better). Port of iOS speedScore. */
    fun speedScore(charger: Charger, vehicle: Vehicle, preferredNetworks: Set<String>): Double {
        val compatibleKw = charger.compatiblePower(vehicle.connectorTypes) ?: 0
        val effectiveSpeed = minOf(compatibleKw.toDouble(), vehicle.maxDcChargingKw.toDouble())
        val availabilityRatio = charger.availabilityRatio ?: 0.6   // neutral when live availability unknown
        val queueRisk = if (charger.availableStalls == null) 0.0 else maxOf(0.0, (1.0 - availabilityRatio) * 40)
        val statusPenalty = when (charger.status) {
            ChargerStatus.BUSY -> 20.0
            ChargerStatus.LIMITED -> 8.0
            else -> 0.0
        }
        val networkBonus = if (networkMatches(charger.network, preferredNetworks)) 25.0 else 0.0

        return effectiveSpeed * 0.35 +
            charger.reliabilityScore * 1.2 +
            availabilityRatio * 30 -
            charger.detourMinutes * 3.5 -
            queueRisk -
            statusPenalty +
            networkBonus +
            charger.numberOfStalls * 0.5
    }
}
