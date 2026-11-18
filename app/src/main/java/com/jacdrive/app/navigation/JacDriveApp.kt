package com.jacdrive.app.navigation

import android.content.res.Configuration
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jacdrive.analytics.performance.PerformanceScreen
import com.jacdrive.analytics.trips.TripDetailScreen
import com.jacdrive.analytics.trips.TripsScreen
import com.jacdrive.maintenance.MaintenanceItemScreen
import com.jacdrive.app.StartupScreen
import com.jacdrive.dashboard.cockpit.CockpitScreen
import com.jacdrive.dashboard.home.HomeDestination
import com.jacdrive.dashboard.home.HomeScreen
import com.jacdrive.diagnostics.DiagnosticsScreen
import com.jacdrive.diagnostics.DtcDetailScreen
import com.jacdrive.diagnostics.HealthScreen
import com.jacdrive.diagnostics.SessionScreen
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.jacdrive.maintenance.MaintenanceScreen
import com.jacdrive.settings.AdapterScreen
import com.jacdrive.settings.SettingsScreen
import com.jacdrive.settings.ThemeStudioScreen
import com.jacdrive.vehicle.VehicleScreen

@Composable
fun JacDriveApp() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val chrome = route != null && route !in Routes.fullScreen
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = if (chrome) androidx.compose.material3.ScaffoldDefaults.contentWindowInsets else WindowInsets(0),
        bottomBar = {
            if (chrome && !landscape) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    MainSection.entries.filter { it.primary }.forEach { s ->
                        NavigationBarItem(
                            selected = entry?.destination?.hierarchy?.any { it.route == s.route } == true,
                            onClick = { nav.navigateToSection(s) },
                            icon = { Icon(s.icon, null) },
                            label = { Text(stringResource(s.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(if (chrome) padding else PaddingValues())) {
            if (chrome && landscape) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxHeight()) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        MainSection.entries.forEach { s ->
                            NavigationRailItem(
                                selected = entry?.destination?.hierarchy?.any { it.route == s.route } == true,
                                onClick = { nav.navigateToSection(s) },
                                icon = { Icon(s.icon, null) },
                                label = { Text(stringResource(s.label), maxLines = 1) },
                            )
                        }
                    }
                }
            }
            JacNavHost(nav, Modifier.fillMaxSize())
        }
    }
}

private fun NavHostController.navigateToSection(section: MainSection) {
    if (section == MainSection.DASHBOARD) {
        navigate(Routes.COCKPIT) { launchSingleTop = true }
        return
    }
    navigate(section.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun JacNavHost(nav: NavHostController, modifier: Modifier) {
    val back: () -> Unit = { nav.popBackStack() }
    NavHost(
        navController = nav,
        startDestination = Routes.STARTUP,
        modifier = modifier,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() },
    ) {
        composable(Routes.STARTUP) {
            StartupScreen(onFinished = {
                nav.navigate(Routes.HOME) { popUpTo(Routes.STARTUP) { inclusive = true } }
            })
        }
        composable(Routes.HOME) {
            HomeScreen(onNavigate = { d ->
                nav.navigate(
                    when (d) {
                        HomeDestination.COCKPIT -> Routes.COCKPIT
                        HomeDestination.DIAGNOSTICS -> Routes.DIAGNOSTICS
                        HomeDestination.PERFORMANCE -> Routes.PERFORMANCE
                        HomeDestination.TRIPS -> Routes.TRIPS
                        HomeDestination.MAINTENANCE -> Routes.MAINTENANCE
                        HomeDestination.VEHICLE -> Routes.VEHICLE
                        HomeDestination.THEMES -> Routes.THEME_STUDIO
                        HomeDestination.SETTINGS -> Routes.SETTINGS
                        HomeDestination.ADAPTER -> Routes.ADAPTER
                    },
                ) { launchSingleTop = true }
            })
        }
        composable(Routes.COCKPIT) {
            CockpitScreen(onBack = back, onOpenThemeStudio = { nav.navigate(Routes.THEME_STUDIO) })
        }
        composable(Routes.DIAGNOSTICS) {
            DiagnosticsScreen(
                onBack = null,
                onOpenHealth = { nav.navigate(Routes.HEALTH) },
                onOpenCode = { nav.navigate(Routes.dtc(it)) },
                onOpenSession = { nav.navigate(Routes.session(it)) },
            )
        }
        composable(Routes.DTC, arguments = listOf(navArgument("code") { type = NavType.StringType })) { e ->
            DtcDetailScreen(code = e.arguments?.getString("code").orEmpty(), onBack = back)
        }
        composable(Routes.SESSION, arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
            SessionScreen(id = e.arguments?.getLong("id") ?: 0L, onBack = back, onOpenCode = { nav.navigate(Routes.dtc(it)) })
        }
        composable(Routes.HEALTH) { HealthScreen(onBack = back) }
        composable(Routes.PERFORMANCE) { PerformanceScreen(onBack = back) }
        composable(Routes.TRIPS) { TripsScreen(onBack = null, onOpenTrip = { nav.navigate(Routes.trip(it)) }) }
        composable(Routes.TRIP, arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
            TripDetailScreen(id = e.arguments?.getLong("id") ?: 0L, onBack = back)
        }
        composable(Routes.MAINTENANCE) { MaintenanceScreen(onBack = null, onOpenItem = { nav.navigate(Routes.maintenanceItem(it)) }) }
        composable(Routes.MAINTENANCE_ITEM, arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
            MaintenanceItemScreen(id = e.arguments?.getLong("id") ?: 0L, onBack = back)
        }
        composable(Routes.VEHICLE) {
            VehicleScreen(
                onBack = back,
                onOpenMaintenance = { nav.navigate(Routes.MAINTENANCE) { launchSingleTop = true } },
                onOpenDiagnostics = { nav.navigate(Routes.DIAGNOSTICS) { launchSingleTop = true } },
                onOpenAdapter = { nav.navigate(Routes.ADAPTER) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = back, onOpenThemeStudio = { nav.navigate(Routes.THEME_STUDIO) }, onOpenAdapter = { nav.navigate(Routes.ADAPTER) })
        }
        composable(Routes.THEME_STUDIO) { ThemeStudioScreen(onBack = back) }
        composable(Routes.ADAPTER) { AdapterScreen(onBack = back) }
    }
}
