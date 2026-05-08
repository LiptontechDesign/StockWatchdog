package com.stockwatchdog.app.ui.dip

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.stockwatchdog.app.di.AppContainer
import com.stockwatchdog.app.ui.dipfinder.DipFinderScreen
import com.stockwatchdog.app.ui.diptracker.DipTrackerScreen
import kotlinx.coroutines.launch

/**
 * Combined Dip page. Hosts the two related sub-features behind a single
 * bottom-nav tab so the user has one mental model for "everything dip":
 *
 *  - **Finder**  : we look for healthy dips automatically + your watchlist
 *  - **Tracker** : your manual buy zones with traffic-light status
 *
 * Each sub-screen is rendered as-is inside a [HorizontalPager], so existing
 * features (sort, filter, swipe actions, refresh, dialogs, etc.) keep
 * working unchanged. The TabRow at the top doubles as a clear label of
 * what you're looking at and as a swipe target.
 */
@Composable
fun DipScreen(
    container: AppContainer,
    onOpenSymbol: (String) -> Unit
) {
    val tabs = listOf(
        DipTab.Finder,
        DipTab.Tracker
    )
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    // Keep the TabRow indicator in sync if the user swipes the pager.
    var selectedTab by remember { mutableIntStateOf(0) }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { selectedTab = it }
    }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = selectedTab == index,
                    onClick = {
                        selectedTab = index
                        scope.launch { pagerState.animateScrollToPage(index) }
                    },
                    text = {
                        Text(
                            tab.label,
                            fontWeight = if (selectedTab == index) FontWeight.SemiBold
                            else FontWeight.Normal
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = null,
                            tint = if (selectedTab == index)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (tabs[page]) {
                DipTab.Finder -> DipFinderScreen(
                    container = container,
                    onOpenSymbol = onOpenSymbol
                )
                DipTab.Tracker -> DipTrackerScreen(
                    container = container,
                    onOpenSymbol = onOpenSymbol
                )
            }
        }
    }
}

private enum class DipTab(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Finder("Finder", Icons.Default.Bolt),
    Tracker("Tracker", Icons.Default.TrendingDown)
}
