package com.jacdrive.app.navigation

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.launch
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import com.jacdrive.dashboard.R
import com.jacdrive.dashboard.dash.DashDestination
import com.jacdrive.dashboard.dash.DashboardScreen
import com.jacdrive.dashboard.dash.LiveDataScreen
import com.jacdrive.ui.components.appBackground
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
fun JacDriveApp(sportMode: Boolean = false, onSport: (Boolean) -> Unit = {}) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val chrome = route != null && route !in Routes.fullScreen
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.2f
    val accent = MaterialTheme.colorScheme.primary
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val openDrawer: () -> Unit = { scope.launch { drawer.open() } }

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = chrome && drawer.isOpen,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Color(0xF20A1428), drawerContentColor = Color.White) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
                    Text(
                        stringResource(R.string.drawer_title),
                        style = MaterialTheme.typography.titleLarge, color = accent,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
                    )
                    DrawerItem.entries.forEach { item ->
                        NavigationDrawerItem(
                            label = { Text(stringResource(item.label)) },
                            icon = { Icon(item.icon, null) },
                            selected = route == item.route,
                            onClick = {
                                scope.launch { drawer.close() }
                                nav.navigate(item.route) { launchSingleTop = true }
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                unselectedContainerColor = Color.Transparent,
                                selectedContainerColor = accent.copy(alpha = 0.2f),
                                unselectedIconColor = Color(0xFF9FB6D6), selectedIconColor = accent,
                                unselectedTextColor = Color.White, selectedTextColor = Color.White,
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = 8.dp), color = Color(0x336FA8FF))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.drawer_sport), modifier = Modifier.weight(1f))
                        Switch(checked = sportMode, onCheckedChange = onSport)
                    }
                }
            }
        },
    ) {
        Box(Modifier.fillMaxSize().appBackground(dark, accent)) {
            Row(
                Modifier.fillMaxSize().then(
                    if (chrome) Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)) else Modifier,
                ),
            ) {
                if (chrome && landscape) {
                    GlassRail(entry, accent, Modifier.fillMaxHeight().padding(8.dp)) { nav.navigateToSection(it) }
                }
                JacNavHost(nav, openDrawer, Modifier.fillMaxSize())
            }
            if (chrome && !landscape) {
                GlassBottomBar(
                    entry, accent,
                    Modifier.align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) { nav.navigateToSection(it) }
            }
        }
    }
}

private fun NavBackStackEntry?.isIn(section: MainSection): Boolean {
    val r = this?.destination?.route ?: return false
    return when (section) {
        MainSection.DTC -> r == Routes.DIAGNOSTICS || r == Routes.DTC || r == Routes.SESSION
        MainSection.SETTINGS -> r == Routes.SETTINGS
        else -> this.destination.hierarchy.any { it.route == section.route }
    }
}

@Composable
private fun GlassBottomBar(entry: NavBackStackEntry?, accent: Color, modifier: Modifier, onSelect: (MainSection) -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier.fillMaxWidth().widthIn(max = 640.dp).height(68.dp)
            .shadow(18.dp, shape, ambientColor = accent, spotColor = accent)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xE6122440), Color(0xF2060C18))))
            .border(1.dp, Brush.horizontalGradient(listOf(accent.copy(alpha = 0.15f), accent.copy(alpha = 0.6f), accent.copy(alpha = 0.15f))), shape)
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MainSection.entries.forEach { s -> BarItem(s, entry.isIn(s), accent, Modifier.weight(1f)) { onSelect(s) } }
    }
}

@Composable
private fun GlassRail(entry: NavBackStackEntry?, accent: Color, modifier: Modifier, onSelect: (MainSection) -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier.width(84.dp).clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xE6122440), Color(0xF2060C18))))
            .border(1.dp, accent.copy(alpha = 0.4f), shape)
            .verticalScroll(rememberScrollState()).selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        MainSection.entries.forEach { s -> BarItem(s, entry.isIn(s), accent, Modifier.fillMaxWidth().height(68.dp)) { onSelect(s) } }
    }
}

@Composable
private fun BarItem(s: MainSection, selected: Boolean, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    val tint by animateColorAsState(if (selected) accent else Color(0xFF8AA0BF), label = "barTint")
    val glow by animateFloatAsState(if (selected) 1f else 0f, label = "barGlow")
    Column(
        modifier.fillMaxHeight()
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .drawBehind {
                if (glow > 0f) {
                    drawCircle(
                        Brush.radialGradient(listOf(accent.copy(alpha = 0.35f * glow), Color.Transparent), center = Offset(size.width / 2, size.height * 0.38f), radius = size.minDimension * 0.6f),
                        radius = size.minDimension * 0.6f, center = Offset(size.width / 2, size.height * 0.38f),
                    )
                    drawLine(accent.copy(alpha = glow), Offset(size.width * 0.3f, size.height - 3.dp.toPx()), Offset(size.width * 0.7f, size.height - 3.dp.toPx()), 3.dp.toPx(), StrokeCap.Round)
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(s.icon, null, tint = tint, modifier = Modifier.size(24.dp))
        Text(stringResource(s.label), style = MaterialTheme.typography.labelSmall, color = if (selected) Color.White else tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun NavHostController.navigateToSection(section: MainSection) {
    navigate(section.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun JacNavHost(nav: NavHostController, openDrawer: () -> Unit, modifier: Modifier) {
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
            DashboardScreen(onOpenDrawer = openDrawer, onNavigate = { d ->
                when (d) {
                    DashDestination.DTC -> nav.navigateToSection(MainSection.DTC)
                    DashDestination.LIVE_DATA -> nav.navigateToSection(MainSection.LIVE_DATA)
                    DashDestination.SETTINGS -> nav.navigateToSection(MainSection.SETTINGS)
                    DashDestination.ADAPTER -> nav.navigate(Routes.ADAPTER) { launchSingleTop = true }
                }
            })
        }
        composable(Routes.LIVE_DATA) { LiveDataScreen(onOpenAdapter = { nav.navigate(Routes.ADAPTER) { launchSingleTop = true } }) }
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
        composable(Routes.HEALTH) { HealthScreen(onBack = null) }
        composable(Routes.PERFORMANCE) { PerformanceScreen(onBack = back) }
        composable(Routes.TRIPS) { TripsScreen(onBack = back, onOpenTrip = { nav.navigate(Routes.trip(it)) }) }
        composable(Routes.TRIP, arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
            TripDetailScreen(id = e.arguments?.getLong("id") ?: 0L, onBack = back)
        }
        composable(Routes.MAINTENANCE) { MaintenanceScreen(onBack = back, onOpenItem = { nav.navigate(Routes.maintenanceItem(it)) }) }
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
            SettingsScreen(
                onBack = null,
                onOpenThemeStudio = { nav.navigate(Routes.THEME_STUDIO) },
                onOpenAdapter = { nav.navigate(Routes.ADAPTER) },
                onOpenAlerts = { nav.navigate(Routes.ALERTS) },
                onOpenDiagnostics = { nav.navigateToSection(MainSection.DTC) },
                onOpenTrips = { nav.navigate(Routes.TRIPS) { launchSingleTop = true } },
            )
        }
        composable(Routes.THEME_STUDIO) { ThemeStudioScreen(onBack = back) }
        composable(Routes.ADAPTER) { AdapterScreen(onBack = back) }
        composable(Routes.ALERTS) { com.jacdrive.settings.AlertsScreen(onBack = back) }
    }
}
