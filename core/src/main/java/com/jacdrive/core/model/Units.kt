package com.jacdrive.core.model

enum class SpeedUnit { KMH, MPH }

enum class TemperatureUnit { CELSIUS, FAHRENHEIT }

/** Pure unit conversions. Internal values are always stored in metric (km/h, °C, km). */
object UnitConverter {
    private const val KM_PER_MILE = 1.609344

    fun speed(kmh: Double, unit: SpeedUnit): Double = when (unit) {
        SpeedUnit.KMH -> kmh
        SpeedUnit.MPH -> kmh / KM_PER_MILE
    }

    fun distance(km: Double, unit: SpeedUnit): Double = when (unit) {
        SpeedUnit.KMH -> km
        SpeedUnit.MPH -> km / KM_PER_MILE
    }

    fun temperature(celsius: Double, unit: TemperatureUnit): Double = when (unit) {
        TemperatureUnit.CELSIUS -> celsius
        TemperatureUnit.FAHRENHEIT -> celsius * 9.0 / 5.0 + 32.0
    }

    /** Max of the speedometer scale in the selected unit. */
    fun speedScaleMax(unit: SpeedUnit): Double = when (unit) {
        SpeedUnit.KMH -> 240.0
        SpeedUnit.MPH -> 160.0
    }
}
