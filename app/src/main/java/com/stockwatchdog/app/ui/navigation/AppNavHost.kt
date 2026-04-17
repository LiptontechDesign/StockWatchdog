package com.stockwatchdog.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.stockwatchdog.app.di.AppContainer
import com.stockwatchdog.app.ui.alerts.AlertsScreen
import com.stockwatchdog.app.ui.detail.TickerDetailScreen
import com.stockwatchdog.app.ui.settings.SettingsScreen
import com.stockwatchdog.app.ui.watchlist.WatchlistScreen

sealed class TopLevel(val route: String, val label: String) {
    data object Watchlist : TopLevel("watchlist", "Watchlist")
    data object Alerts : TopLevel("alerts", "Alerts")
    data object Settings : TopLevel("settings", "Settings")
}

object Routes {
    const val DETAIL = "detail"
    fun detail(symbol: String) = "$DETAIL/$symbol"
}

@Composable
fun AppNavHost(
    container: AppContainer,
    initialDeepLinkSymbol: String?,
    onDeepLinkConsumed: () -> Unit
) {
    val navController = rememberNavController()

    LaunchedEffect(initialDeepLinkSymbol) {
        val sym = initialDeepLinkSymbol
        if (!sym.isNullOrBlank()) {
            navController.navigate(Routes.detail(sym))
            onDeepLinkConsumed()
        }
    }

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = currentRoute in listOf(
        TopLevel.Watchlist.route,
        TopLevel.Alerts.route,
        TopLevel.Settings.route
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    val items = listOf(TopLevel.Watchlist, TopLevel.Alerts, TopLevel.Settings)
                    items.forEach { item ->
                        val selected = backStack?.destination?.hierarchy
                            ?.any { it.route == item.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = when (item) {
                                        TopLevel.Watchlist -> Icons.Default.ShowChart
                                        TopLevel.Alerts -> Icons.Default.NotificationsActive
                                        TopLevel.Settings -> Icons.Default.Settings
                                    },
                                    contentDescription = item.label
                                )
                            },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            NavHost(
                navController = navController,
                startDestination = TopLevel.Watchlist.route
            ) {
                composable(TopLevel.Watchlist.route) {
                    WatchlistScreen(
                        container = container,
                        onOpenSymbol = { sym -> navController.navigate(Routes.detail(sym)) }
                    )
                }
                composable(TopLevel.Alerts.route) {
                    AlertsScreen(
                        container = container,
                        onOpenSymbol = { sym -> navController.navigate(Routes.detail(sym)) }
                    )
                }
                composable(TopLevel.Settings.route) {
                    SettingsScreen(container = container)
                }
                composable(
                    route = "${Routes.DETAIL}/{symbol}",
                    arguments = listOf(navArgument("symbol") { type = NavType.StringType })
                ) { entry ->
                    val symbol = entry.arguments?.getString("symbol").orEmpty()
                    TickerDetailScreen(
                        container = container,
                        symbol = symbol,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
