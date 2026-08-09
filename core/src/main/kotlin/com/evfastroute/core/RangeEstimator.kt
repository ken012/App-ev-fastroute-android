package com.evfastroute.core

// Real-world EV range estimation. Faithful port of iOS RangeEstimator (Swift). Corrects the
// vehicle's reference efficiency for battery health, weather (expressed as range loss), route
// speed, payload and driving style, then adds a bounded uncertainty margin for what the app
// can't observe (wind, rain, elevation, tyre pressure).

enum class RangeDrivingStyle {
    EFFICIENT, BALANCED, BRISK;

    /** Matches the Swift raw value for cross-platform persistence. */
    val serialized: String
        get() = when (this) {
            EFFICIENT -> "efficient"
            BALANCED -> "balanced"
            BRISK -> "brisk"
        }

    val consumptionMultiplier: Double
        get() = when (this) {
            EFFICIENT -> 0.95
            BALANCED -> 1.0
            BRISK -> 1.08
        }

    companion object {
        fun fromSerialized(value: String): RangeDrivingStyle =
            entries.firstOrNull { it.serialized == value } ?: BALANCED
    }
}

object RangeEstimator {

    data class Estimate(
        val usableCapacityKwh: Double,
        val expectedEfficiencyKwhPerKm: Double,
        val planningEfficiencyKwhPerKm: Double,
        val expectedRangeKm: Double,
        val conservativeRangeKm: Double,
        val speedMultiplier: Double,
        val weatherMultiplier: Double,
        val uncertaintyPercent: Double,
    )

    fun estimate(
        vehicle: Vehicle,
        currentBatteryPercent: Double,
        arrivalBufferPercent: Double,
        weatherRangeLossPercent: Double,
        extraLoadKg: Double,
        drivingStyle: RangeDrivingStyle,
        averageSpeedKph: Double? = null,
    ): Estimate {
        val rawHealth = vehicle.batteryHealthPercent ?: 100.0
        val health = bounded(rawHealth, fallback = 100.0, low = 50.0, high = 100.0) / 100
        val baseCapacity = bounded(vehicle.batteryCapacityKwh, fallback = 75.0, low = 1.0, high = 300.0)
        val usableCapacity = maxOf(1.0, baseCapacity * health)
        val currentSOC = bounded(currentBatteryPercent, fallback = 70.0, low = 0.0, high = 100.0)
        val arrivalReserve = bounded(arrivalBufferPercent, fallback = 15.0, low = 0.0, high = 50.0)
        val usableSOC = maxOf(0.0, currentSOC - arrivalReserve)
        val availableEnergy = usableCapacity * usableSOC / 100

        // Range loss → equivalent consumption increase: a 20% loss means energy/km × 1/(1-0.20).
        val weatherLossPercent = bounded(weatherRangeLossPercent, fallback = 0.0, low = 0.0, high = 55.0)
        val weatherLoss = weatherLossPercent / 100
        val weatherMultiplier = 1 / maxOf(0.45, 1 - weatherLoss)
        val speedMultiplier = speedConsumptionMultiplier(averageSpeedKph)
        // No curb-weight field: +100 kg ≈ +2% reference consumption, capped so a bad input can't dominate.
        val load = bounded(extraLoadKg, fallback = 0.0, low = 0.0, high = 750.0)
        val loadMultiplier = 1 + minOf(0.15, load / 5_000)

        val referenceEfficiency = bounded(vehicle.efficiencyKwhPerKm, fallback = 0.20, low = 0.05, high = 0.60)
        val expectedEfficiency = maxOf(
            0.05,
            referenceEfficiency *
                weatherMultiplier *
                speedMultiplier *
                loadMultiplier *
                drivingStyle.consumptionMultiplier,
        )

        val speedUncertainty = maxOf(0.0, speedMultiplier - 1) * 0.10
        val uncertainty = minOf(0.12, 0.05 + weatherLoss * 0.08 + speedUncertainty)
        val planningEfficiency = expectedEfficiency * (1 + uncertainty)
        val expectedRange = availableEnergy / expectedEfficiency
        val conservativeRange = availableEnergy / planningEfficiency

        return Estimate(
            usableCapacityKwh = usableCapacity,
            expectedEfficiencyKwhPerKm = expectedEfficiency,
            planningEfficiencyKwhPerKm = planningEfficiency,
            expectedRangeKm = maxOf(0.0, expectedRange),
            conservativeRangeKm = maxOf(0.0, conservativeRange),
            speedMultiplier = speedMultiplier,
            weatherMultiplier = weatherMultiplier,
            uncertaintyPercent = uncertainty * 100,
        )
    }

    /** Vehicle whose capacity/consumption are pre-adjusted for the SOC & charge planners. */
    fun planningVehicle(
        from: Vehicle,
        currentBatteryPercent: Double,
        arrivalBufferPercent: Double,
        weatherRangeLossPercent: Double,
        extraLoadKg: Double,
        drivingStyle: RangeDrivingStyle,
        averageSpeedKph: Double?,
    ): Vehicle {
        val e = estimate(
            vehicle = from,
            currentBatteryPercent = currentBatteryPercent,
            arrivalBufferPercent = arrivalBufferPercent,
            weatherRangeLossPercent = weatherRangeLossPercent,
            extraLoadKg = extraLoadKg,
            drivingStyle = drivingStyle,
            averageSpeedKph = averageSpeedKph,
        )
        return from.copy(
            batteryCapacityKwh = e.usableCapacityKwh,
            efficiencyKwhPerKm = e.planningEfficiencyKwhPerKm,
        )
    }

    fun averageSpeedKph(distanceKm: Double, travelTimeSeconds: Double): Double? {
        if (!distanceKm.isFinite() || !travelTimeSeconds.isFinite() || distanceKm <= 0 || travelTimeSeconds <= 0) {
            return null
        }
        return minOf(140.0, maxOf(5.0, distanceKm / (travelTimeSeconds / 3_600)))
    }

    fun speedConsumptionMultiplier(averageSpeedKph: Double?): Double {
        val rawSpeed = averageSpeedKph ?: return 1.0
        if (!rawSpeed.isFinite()) return 1.0
        val speed = minOf(130.0, maxOf(15.0, rawSpeed))
        return when {
            speed < 35 -> 0.93
            speed < 60 -> interpolate(speed, 35.0, 60.0, 0.93, 0.98)
            speed < 80 -> interpolate(speed, 60.0, 80.0, 0.98, 1.03)
            speed < 100 -> interpolate(speed, 80.0, 100.0, 1.03, 1.14)
            speed < 115 -> interpolate(speed, 100.0, 115.0, 1.14, 1.24)
            else -> interpolate(speed, 115.0, 130.0, 1.24, 1.34)
        }
    }

    private fun interpolate(value: Double, inLow: Double, inHigh: Double, outLow: Double, outHigh: Double): Double {
        val fraction = (value - inLow) / maxOf(0.0001, inHigh - inLow)
        return outLow + minOf(1.0, maxOf(0.0, fraction)) * (outHigh - outLow)
    }

    private fun bounded(value: Double, fallback: Double, low: Double, high: Double): Double {
        if (!value.isFinite()) return fallback
        return minOf(high, maxOf(low, value))
    }
}
