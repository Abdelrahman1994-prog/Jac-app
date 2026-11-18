package com.jacdrive.dashboard.cockpit

import com.jacdrive.core.model.CockpitPreferences
import com.jacdrive.core.model.DashboardWidget
import com.jacdrive.core.model.Freshness
import com.jacdrive.core.model.SpeedUnit
import com.jacdrive.core.model.TemperatureUnit
import com.jacdrive.core.model.VehicleMetric
import com.jacdrive.core.model.VehicleSnapshot
import com.jacdrive.core.model.freshness
import com.jacdrive.ui.components.DisplayUnits
import com.jacdrive.ui.components.format
import com.jacdrive.ui.components.unitLabel

data class CockpitUiState(
    val snapshot: VehicleSnapshot = VehicleSnapshot.offline(),
    val prefs: CockpitPreferences = CockpitPreferences(),
    val units: DisplayUnits = DisplayUnits(SpeedUnit.KMH, TemperatureUnit.CELSIUS),
) {
    fun visible(widget: DashboardWidget) = widget in prefs.visibleWidgets

    /** Secondary widgets shown as tiles, in a stable order. */
    val tileWidgets: List<DashboardWidget>
        get() = TILE_ORDER.filter(::visible)

    companion object {
        val TILE_ORDER = listOf(
            DashboardWidget.COOLANT_TEMP,
            DashboardWidget.BATTERY_VOLTAGE,
            DashboardWidget.ENGINE_LOAD,
            DashboardWidget.THROTTLE,
            DashboardWidget.FUEL_LEVEL,
            DashboardWidget.INTAKE_TEMP,
            DashboardWidget.TRIP_DISTANCE,
            DashboardWidget.AVG_FUEL,
        )
    }
}

/** What a single metric looks like on screen right now. */
data class MetricDisplay(
    val raw: Double?,
    val text: String?,
    val unit: String,
    val freshness: Freshness,
    val unsupported: Boolean,
)

fun VehicleSnapshot.display(metric: VehicleMetric, units: DisplayUnits, nowMs: Long): MetricDisplay {
    val v = this[metric]
    return MetricDisplay(
        raw = v?.value,
        text = v?.let { metric.format(it.value, units) },
        unit = metric.unitLabel(units),
        freshness = v.freshness(metric, nowMs),
        unsupported = isUnsupported(metric),
    )
}

/** Fraction 0..1 used for bar indicators, per metric. Null when the metric has no natural scale. */
fun barFraction(metric: VehicleMetric, raw: Double?): Float? {
    raw ?: return null
    val f = when (metric) {
        VehicleMetric.COOLANT_TEMP -> (raw - 40) / (130 - 40)
        VehicleMetric.BATTERY_VOLTAGE -> (raw - 10) / (16 - 10)
        VehicleMetric.ENGINE_LOAD, VehicleMetric.THROTTLE_POSITION, VehicleMetric.FUEL_LEVEL -> raw / 100
        VehicleMetric.INTAKE_AIR_TEMP -> (raw + 10) / (80 + 10)
        else -> return null
    }
    return f.toFloat().coerceIn(0f, 1f)
}
