package com.jacdrive.core.model

enum class CockpitThemeId { JAC_ORIGINAL, RACING_RED, CYBER_BLUE, CARBON_BLACK, SPORT_GT, HORIZON }

enum class GaugeLayout {
    /** Speedometer and tachometer side by side, info column in the middle. */
    DUAL_DIAL,

    /** One large tachometer with digital speed in its centre, widgets on both sides. */
    SPORT_CENTER,

    /** Large digital numbers with bar indicators. */
    DIGITAL,

    /** Minimal twin rings with sub-gauges and a central road view. */
    HORIZON,
}

enum class DashboardBackground { THEME_DEFAULT, PURE_BLACK, GRADIENT, CARBON, GRID }

/** Widgets the user can show or hide on the cockpit. */
enum class DashboardWidget(val metric: VehicleMetric?) {
    SPEED(VehicleMetric.SPEED),
    RPM(VehicleMetric.RPM),
    COOLANT_TEMP(VehicleMetric.COOLANT_TEMP),
    BATTERY_VOLTAGE(VehicleMetric.BATTERY_VOLTAGE),
    ENGINE_LOAD(VehicleMetric.ENGINE_LOAD),
    THROTTLE(VehicleMetric.THROTTLE_POSITION),
    FUEL_LEVEL(VehicleMetric.FUEL_LEVEL),
    INTAKE_TEMP(VehicleMetric.INTAKE_AIR_TEMP),
    AMBIENT_TEMP(VehicleMetric.AMBIENT_TEMP),
    TRIP_DISTANCE(VehicleMetric.TRIP_DISTANCE),
    AVG_FUEL(VehicleMetric.AVG_FUEL_CONSUMPTION),
    CONNECTION(null),
}

data class CockpitPreferences(
    val themeId: CockpitThemeId = CockpitThemeId.JAC_ORIGINAL,
    /** ARGB accent override, or null to use the theme's own accent. */
    val accentOverride: Long? = null,
    val layout: GaugeLayout = GaugeLayout.DUAL_DIAL,
    val background: DashboardBackground = DashboardBackground.THEME_DEFAULT,
    val animationsEnabled: Boolean = true,
    val visibleWidgets: Set<DashboardWidget> = DashboardWidget.entries.toSet(),
    /** RPM where the tachometer red zone starts. User configurable, no undocumented default assumed as fact. */
    val redlineRpm: Int = 6000,
    val keepScreenOn: Boolean = true,
    /** Sport mode: aggressive red/orange accents and stronger gauge illumination. */
    val sportMode: Boolean = false,
)
