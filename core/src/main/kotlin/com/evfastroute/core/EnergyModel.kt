package com.evfastroute.core

// Trip-level SOC/energy feasibility. Faithful port of iOS RouteOptimizationService.EnergyPlan /
// energyPlan. Decides whether a leg needs charging and the arrival battery if it doesn't.

/** Mirrors iOS `EnergyPlan`. */
data class EnergyPlan(
    val deficitKwh: Double,
    val tripDrainPct: Double,
    val arrivalIfNoChargePct: Int,
) {
    val needsCharge: Boolean get() = deficitKwh > 0.0
}

object EnergyModel {

    fun energyPlan(
        distanceKm: Double,
        capacityKwh: Double,
        efficiencyKwhPerKm: Double,
        currentBatteryPercent: Double,
        arrivalBufferPercent: Double,
    ): EnergyPlan {
        val capacity = maxOf(1.0, capacityKwh)
        val startEnergy = capacity * currentBatteryPercent / 100.0
        val tripEnergy = distanceKm * efficiencyKwhPerKm
        val reserveEnergy = capacity * arrivalBufferPercent / 100.0
        val deficit = tripEnergy + reserveEnergy - startEnergy
        val tripDrainPct = minOf(100.0, tripEnergy / capacity * 100.0)
        // Swift `Int(Double)` truncates toward zero; Kotlin `toInt()` matches, then clamp 0…100.
        val arrivalPct = (((startEnergy - tripEnergy) / capacity) * 100.0).toInt().coerceIn(0, 100)
        return EnergyPlan(deficitKwh = deficit, tripDrainPct = tripDrainPct, arrivalIfNoChargePct = arrivalPct)
    }
}
