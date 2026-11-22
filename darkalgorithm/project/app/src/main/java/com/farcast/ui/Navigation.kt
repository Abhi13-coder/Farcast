package com.farcast.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.farcast.ui.theme.*

private data class RailItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

/**
 * The left branch rail from DESIGN_NOTES — a switch bank, not a bottom nav
 * bar of app-store-style tabs. Five destinations for this pass; Domain/
 * Persona/IP-Threat/Cams/Search results open from within the manifest
 * (tapping a Lead), not as separate top-level rail items — the case is the
 * organizing structure, branches are facets of it, not siblings of it.
 */
private val RAIL_ITEMS = listOf(
    RailItem("manifest", "Case", Icons.Outlined.Folder),
    RailItem("pipeline", "Pipeline", Icons.Outlined.Timeline),
    RailItem("generator", "New", Icons.Outlined.Add),
    RailItem("plugins", "Plugins", Icons.Outlined.Extension),
    RailItem("settings", "Settings", Icons.Outlined.Settings)
)

@Composable
fun FarcastNavHost(screens: FarcastScreens) {
    val navController = rememberNavController()

    Row(Modifier.fillMaxSize().background(FarcastBackground)) {
        BranchRail(navController)
        Box(Modifier.weight(1f)) {
            NavHost(navController, startDestination = "manifest") {
                composable("manifest") { screens.manifest() }
                composable("pipeline") { screens.pipeline() }
                composable("generator") { screens.generator() }
                composable("plugins") { screens.plugins() }
                composable("settings") { screens.settings() }
                composable("moment_finder") { screens.momentFinder() }
                composable("location_picker") { screens.locationPicker() }
            }
        }
    }
}

@Composable
private fun BranchRail(navController: androidx.navigation.NavController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    NavigationRail(containerColor = FarcastPanel, contentColor = FarcastTextPrimary) {
        RAIL_ITEMS.forEach { item ->
            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
            NavigationRailItem(
                selected = selected,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label, style = FarcastDataStyleSmall) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = FarcastAmber, selectedTextColor = FarcastAmber,
                    unselectedIconColor = FarcastTextMuted, unselectedTextColor = FarcastTextMuted,
                    indicatorColor = FarcastPanelRaised
                )
            )
        }
    }
}

/** Screens are injected as composable lambdas rather than this file knowing
 *  about every ViewModel directly — keeps Navigation.kt as pure wiring. */
data class FarcastScreens(
    val manifest: @Composable () -> Unit,
    val pipeline: @Composable () -> Unit,
    val generator: @Composable () -> Unit,
    val plugins: @Composable () -> Unit,
    val settings: @Composable () -> Unit,
    val momentFinder: @Composable () -> Unit,
    val locationPicker: @Composable () -> Unit
)
