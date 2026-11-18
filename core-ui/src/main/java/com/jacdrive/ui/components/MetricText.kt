package com.jacdrive.ui.components

import androidx.annotation.StringRes
import com.jacdrive.core.model.CockpitThemeId
import com.jacdrive.core.model.SpeedUnit
import com.jacdrive.core.model.TemperatureUnit
import com.jacdrive.core.model.UnitConverter
import com.jacdrive.core.model.VehicleMetric
import com.jacdrive.core.util.Num
import com.jacdrive.ui.R

@StringRes
fun VehicleMetric.labelRes(): Int = when (this) {
    VehicleMetric.SPEED -> R.string.metric_speed
    VehicleMetric.RPM -> R.string.metric_rpm
    VehicleMetric.COOLANT_TEMP -> R.string.metric_coolant
    VehicleMetric.BATTERY_VOLTAGE -> R.string.metric_battery
    VehicleMetric.ENGINE_LOAD -> R.string.metric_load
    VehicleMetric.THROTTLE_POSITION -> R.string.metric_throttle
    VehicleMetric.FUEL_LEVEL -> R.string.metric_fuel
    VehicleMetric.INTAKE_AIR_TEMP -> R.string.metric_intake
    VehicleMetric.INTAKE_MANIFOLD_PRESSURE -> R.string.metric_map
    VehicleMetric.TIMING_ADVANCE -> R.string.metric_timing
    VehicleMetric.TRIP_DISTANCE -> R.string.metric_trip
    VehicleMetric.AVG_FUEL_CONSUMPTION -> R.string.metric_avg_fuel
}

@StringRes
fun CockpitThemeId.labelRes(): Int = when (this) {
    CockpitThemeId.JAC_ORIGINAL -> R.string.theme_jac_original
    CockpitThemeId.RACING_RED -> R.string.theme_racing_red
    CockpitThemeId.CYBER_BLUE -> R.string.theme_cyber_blue
    CockpitThemeId.CARBON_BLACK -> R.string.theme_carbon_black
    CockpitThemeId.SPORT_GT -> R.string.theme_sport_gt
}

/** Display units chosen by the user. */
data class DisplayUnits(val speed: SpeedUnit, val temperature: TemperatureUnit)

/** Converts a metric value (stored metric) into the user's units and formats it. */
fun VehicleMetric.format(value: Double, units: DisplayUnits): String = when (this) {
    VehicleMetric.SPEED -> Num.int(UnitConverter.speed(value, units.speed))
    VehicleMetric.RPM -> Num.int(value)
    VehicleMetric.COOLANT_TEMP, VehicleMetric.INTAKE_AIR_TEMP -> Num.int(UnitConverter.temperature(value, units.temperature))
    VehicleMetric.BATTERY_VOLTAGE -> Num.one(value)
    VehicleMetric.ENGINE_LOAD, VehicleMetric.THROTTLE_POSITION, VehicleMetric.FUEL_LEVEL -> Num.int(value)
    VehicleMetric.INTAKE_MANIFOLD_PRESSURE -> Num.int(value)
    VehicleMetric.TIMING_ADVANCE -> Num.one(value)
    VehicleMetric.TRIP_DISTANCE -> Num.one(UnitConverter.distance(value, units.speed))
    VehicleMetric.AVG_FUEL_CONSUMPTION -> Num.one(value)
}

fun VehicleMetric.unitLabel(units: DisplayUnits): String = when (this) {
    VehicleMetric.SPEED -> if (units.speed == SpeedUnit.KMH) "km/h" else "mph"
    VehicleMetric.TRIP_DISTANCE -> if (units.speed == SpeedUnit.KMH) "km" else "mi"
    VehicleMetric.COOLANT_TEMP, VehicleMetric.INTAKE_AIR_TEMP ->
        if (units.temperature == TemperatureUnit.CELSIUS) "°C" else "°F"
    else -> metricUnit
}

@StringRes
fun com.jacdrive.core.model.GaugeLayout.layoutLabelRes(): Int = when (this) {
    com.jacdrive.core.model.GaugeLayout.DUAL_DIAL -> R.string.layout_dual
    com.jacdrive.core.model.GaugeLayout.SPORT_CENTER -> R.string.layout_sport
    com.jacdrive.core.model.GaugeLayout.DIGITAL -> R.string.layout_digital
}

@StringRes
fun com.jacdrive.core.model.DashboardBackground.labelRes(): Int = when (this) {
    com.jacdrive.core.model.DashboardBackground.THEME_DEFAULT -> R.string.bg_theme_default
    com.jacdrive.core.model.DashboardBackground.PURE_BLACK -> R.string.bg_black
    com.jacdrive.core.model.DashboardBackground.GRADIENT -> R.string.bg_gradient
    com.jacdrive.core.model.DashboardBackground.CARBON -> R.string.bg_carbon
    com.jacdrive.core.model.DashboardBackground.GRID -> R.string.bg_grid
}

@StringRes
fun com.jacdrive.core.model.DashboardWidget.labelRes(): Int =
    metric?.labelRes() ?: R.string.metric_connection
