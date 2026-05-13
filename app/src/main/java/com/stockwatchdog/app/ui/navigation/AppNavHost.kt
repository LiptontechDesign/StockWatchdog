package com.stockwatchdog.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TrendingDown
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.stockwatchdog.app.di.AppContainer
import com.stockwatchdog.app.ui.alerts.AlertsScreen
import com.stockwatchdog.app.ui.detail.TickerDetailScreen
import com.stockwatchdog.app.ui.dip.DipScreen
import com.stockwatchdog.app.ui.portfolio.PortfolioScreen
import com.stockwatchdog.app.ui.settings.SettingsScreen
import com.stockwatchdog.app.ui.watchlist.WatchlistScreen

sealed class TopLevel(val route: String, val label: String) {
    data object Watchlist : TopLevel("watchlist", "Watchlist")
    data object Portfolio : TopLevel("portfolio", "Portfolio")
    data object Dip : TopLevel("dip", "Dip")
    data object Alerts : TopLevel("alerts", "Alerts")
    data object Settings : TopLevel("settings", "Settings")
}

object Routes {
    const val DETAIL = "detail"
    fun detail(symbol: String) = "$DETAIL/$symbol"
    fun detailFinancials(symbol: String) = "$DETAIL/$symbol?tab=financials"
}

@Composable
fun AppNavHost(
    container: AppContainer,
    initialDeepLinkRoute: String?,
    onDeepLinkConsumed: () -> Unit
) {
    val navController = rememberNavController()

    LaunchedEffect(initialDeepLinkRoute) {
        val route = initialDeepLinkRoute
        if (!route.isNullOrBlank()) {
            navController.navigate(route)
            onDeepLinkConsumed()
        }
    }

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = currentRoute in listOf(
        TopLevel.Watchlist.route,
        TopLevel.Portfolio.route,
        TopLevel.Dip.route,
        TopLevel.Alerts.route,
        TopLevel.Settings.route
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    val items = listOf(
                        TopLevel.Watchlist,
                        TopLevel.Portfolio,
                        TopLevel.Dip,
                        TopLevel.Alerts,
                        TopLevel.Settings
                    )
                    items.forEach { item ->
                        val selected = backStack?.destination?.hierarchy
                            ?.any { it.route == item.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    if (item == TopLevel.Watchlist) {
                                        val returnedToWatchlist = navController.popBackStack(
                                            TopLevel.Watchlist.route,
                                            inclusive = false
                                        )
                                        if (!returnedToWatchlist) {
                                            navController.navigate(TopLevel.Watchlist.route) {
                                                launchSingleTop = true
                                            }
                                        }
                                    } else {
                                        navController.navigate(item.route) {
                                            popUpTo(TopLevel.Watchlist.route) {
                                                inclusive = false
                                            }
                                            launchSingleTop = true
                                        }
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = when (item) {
                                        TopLevel.Watchlist -> Icons.Default.ShowChart
                                        TopLevel.Portfolio -> Icons.Default.PieChart
                                        TopLevel.Dip -> Icons.Default.TrendingDown
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
                composable(TopLevel.Portfolio.route) {
                    PortfolioScreen(
                        container = container,
                        onOpenSymbol = { sym -> navController.navigate(Routes.detail(sym)) }
                    )
                }
                composable(TopLevel.Dip.route) {
                    DipScreen(
                        container = container,
                        onOpenSymbol = { sym -> navController.navigate(Routes.detail(sym)) },
                        onOpenFinancials = { sym -> navController.navigate(Routes.detailFinancials(sym)) }
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
                    route = "${Routes.DETAIL}/{symbol}?tab={tab}",
                    arguments = listOf(
                        navArgument("symbol") { type = NavType.StringType },
                        navArgument("tab") {
                            type = NavType.StringType
                            defaultValue = ""
                        }
                    )
                ) { entry ->
                    val symbol = entry.arguments?.getString("symbol").orEmpty()
                    val initialTab = when (entry.arguments?.getString("tab")?.lowercase()) {
                        "alerts" -> 1
                        "financials" -> 2
                        else -> 0
                    }
                    TickerDetailScreen(
                        container = container,
                        symbol = symbol,
                        initialTab = initialTab,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
