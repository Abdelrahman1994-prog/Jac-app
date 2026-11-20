package com.jacdrive.app.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.ui.graphics.vector.ImageVector
import com.jacdrive.dashboard.R

object Routes {
    const val STARTUP = "startup"
    const val HOME = "home"
    const val LIVE_DATA = "live"
    const val COCKPIT = "cockpit"
    const val DIAGNOSTICS = "diagnostics"
    const val HEALTH = "diagnostics/health"
    const val DTC = "diagnostics/code/{code}"
    const val SESSION = "diagnostics/session/{id}"
    fun dtc(code: String) = "diagnostics/code/$code"
    fun session(id: Long) = "diagnostics/session/$id"
    const val PERFORMANCE = "performance"
    const val TRIPS = "trips"
    const val MAINTENANCE = "maintenance"
    const val MAINTENANCE_ITEM = "maintenance/item/{id}"
    const val TRIP = "trips/{id}"
    fun maintenanceItem(id: Long) = "maintenance/item/$id"
    fun trip(id: Long) = "trips/$id"
    const val VEHICLE = "vehicle"
    const val SETTINGS = "settings"
    const val THEME_STUDIO = "settings/themes"
    const val ADAPTER = "settings/adapter"
    const val ALERTS = "settings/alerts"

    /** Routes that take over the whole screen (no navigation chrome). */
    val fullScreen = setOf(STARTUP, COCKPIT)
}

/** The five sections of the floating bottom bar. */
enum class MainSection(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    DASHBOARD(Routes.HOME, R.string.nav_main, Icons.Rounded.Dashboard),
    DIAGNOSTICS(Routes.HEALTH, R.string.nav_diagnostics, Icons.Rounded.HealthAndSafety),
    LIVE_DATA(Routes.LIVE_DATA, R.string.nav_live, Icons.Rounded.ShowChart),
    DTC(Routes.DIAGNOSTICS, R.string.nav_dtc, Icons.Rounded.WarningAmber),
    SETTINGS(Routes.SETTINGS, R.string.nav_settings, Icons.Rounded.Settings),
}

/** Everything else, reached from the hamburger drawer. */
enum class DrawerItem(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    COCKPIT(Routes.COCKPIT, R.string.nav_dashboard, Icons.Rounded.Speed),
    TRIPS(Routes.TRIPS, R.string.nav_trips, Icons.Rounded.Route),
    PERFORMANCE(Routes.PERFORMANCE, R.string.nav_performance, Icons.Rounded.Timer),
    MAINTENANCE(Routes.MAINTENANCE, R.string.nav_maintenance, Icons.Rounded.Build),
    VEHICLE(Routes.VEHICLE, R.string.nav_vehicle, Icons.Rounded.DirectionsCar),
    ADAPTER(Routes.ADAPTER, R.string.drawer_adapter, Icons.Rounded.Bluetooth),
    ALERTS(Routes.ALERTS, R.string.drawer_alerts, Icons.Rounded.NotificationsActive),
    THEMES(Routes.THEME_STUDIO, R.string.drawer_themes, Icons.Rounded.Palette),
}
