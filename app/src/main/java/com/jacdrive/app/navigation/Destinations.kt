package com.jacdrive.app.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ManageSearch
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.ui.graphics.vector.ImageVector
import com.jacdrive.dashboard.R

object Routes {
    const val STARTUP = "startup"
    const val HOME = "home"
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

    /** Routes that take over the whole screen (no navigation chrome). */
    val fullScreen = setOf(STARTUP, COCKPIT)
}

/** The eight main sections. [primary] ones are shown in the portrait bottom bar. */
enum class MainSection(val route: String, @StringRes val label: Int, val icon: ImageVector, val primary: Boolean) {
    HOME(Routes.HOME, R.string.nav_home, Icons.Rounded.Home, true),
    DASHBOARD(Routes.COCKPIT, R.string.nav_dashboard, Icons.Rounded.Speed, true),
    DIAGNOSTICS(Routes.DIAGNOSTICS, R.string.nav_diagnostics, Icons.AutoMirrored.Rounded.ManageSearch, true),
    PERFORMANCE(Routes.PERFORMANCE, R.string.nav_performance, Icons.Rounded.Timer, false),
    TRIPS(Routes.TRIPS, R.string.nav_trips, Icons.Rounded.Route, true),
    MAINTENANCE(Routes.MAINTENANCE, R.string.nav_maintenance, Icons.Rounded.Build, true),
    VEHICLE(Routes.VEHICLE, R.string.nav_vehicle, Icons.Rounded.DirectionsCar, false),
    SETTINGS(Routes.SETTINGS, R.string.nav_settings, Icons.Rounded.Settings, false),
}
