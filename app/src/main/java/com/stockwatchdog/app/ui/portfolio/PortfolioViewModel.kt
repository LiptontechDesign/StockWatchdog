package com.stockwatchdog.app.ui.portfolio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stockwatchdog.app.data.api.MarketDataRepository
import com.stockwatchdog.app.data.db.PositionLotDao
import com.stockwatchdog.app.data.db.WatchlistDao
import com.stockwatchdog.app.data.db.entities.PositionLotEntity
import com.stockwatchdog.app.data.prefs.SettingsRepository
import com.stockwatchdog.app.domain.DataResult
import com.stockwatchdog.app.domain.PositionCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HoldingRow(
    val symbol: String,
    val name: String?,
    val platform: String?,
    val totalInvested: Double,
    val currentValue: Double?,
    val pnl: Double?,
    val percentPnl: Double?,
    val currentPrice: Double?
)

data class PortfolioUiState(
    val holdings: List<HoldingRow> = emptyList(),
    val totalInvested: Double = 0.0,
    val totalValue: Double? = null,
    val totalPnl: Double? = null,
    val totalPercentPnl: Double? = null,
    val platformFeePercent: Double = 0.0,
    val isRefreshing: Boolean = false
)

class PortfolioViewModel(
    private val watchlistDao: WatchlistDao,
    private val positionLotDao: PositionLotDao,
    private val repo: MarketDataRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(PortfolioUiState())
    val ui: StateFlow<PortfolioUiState> = _ui.asStateFlow()
    private var latestLots: List<PositionLotEntity> = emptyList()
    private var latestPlatformFeePercent: Double = 0.0

    init {
        viewModelScope.launch {
            positionLotDao.observeAll().collectLatest { allLots ->
                latestLots = allLots
                loadPortfolio(allLots = allLots, forceRefreshQuotes = false)
            }
        }
        viewModelScope.launch {
            settingsRepository.settings.collectLatest { settings ->
                latestPlatformFeePercent = settings.platformFeePercent
                _ui.update { it.copy(platformFeePercent = settings.platformFeePercent) }
                loadPortfolio(allLots = latestLots, forceRefreshQuotes = false)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            loadPortfolio(allLots = latestLots, forceRefreshQuotes = true)
        }
    }

    private suspend fun loadPortfolio(
        allLots: List<PositionLotEntity>,
        forceRefreshQuotes: Boolean
    ) {
        _ui.update { it.copy(isRefreshing = true) }
        // Group by (symbol, platform) so the same ticker on different brokers
        // shows as separate holdings.
        val lotGroups = allLots.groupBy { it.symbol to (it.platform ?: "") }

        if (lotGroups.isEmpty()) {
            _ui.update {
                it.copy(
                    holdings = emptyList(),
                    totalInvested = 0.0,
                    totalValue = null,
                    totalPnl = null,
                    totalPercentPnl = null,
                    isRefreshing = false
                )
            }
            return
        }

        val holdings = mutableListOf<HoldingRow>()
        // Cache quotes per symbol so we don't fetch the same ticker twice.
        val quoteCache = mutableMapOf<String, Double?>()

        for (((symbol, platform), groupLots) in lotGroups) {
            val watchItem = watchlistDao.getBySymbol(symbol)
            val name = watchItem?.name

            val currentPrice = quoteCache.getOrPut(symbol) {
                when (val r = repo.getQuote(symbol, forceRefresh = forceRefreshQuotes)) {
                    is DataResult.Success -> r.value.price
                    is DataResult.Error -> null
                }
            }

            val positionPnl = PositionCalculator.calculate(
                currentPrice = currentPrice,
                lots = groupLots,
                platformFeePercent = latestPlatformFeePercent
            )

            val invested = positionPnl.totalInvested ?: 0.0
            val currentValue = positionPnl.positionValue
            val pnl = positionPnl.totalPnl
            val percentPnl = positionPnl.percentPnl

            holdings.add(
                HoldingRow(
                    symbol = symbol,
                    name = name,
                    platform = platform.ifBlank { null },
                    totalInvested = invested,
                    currentValue = currentValue,
                    pnl = pnl,
                    percentPnl = percentPnl,
                    currentPrice = currentPrice
                )
            )
        }

        val totalInvested = holdings.sumOf { it.totalInvested }
        val allValuesAvailable = holdings.all { it.currentValue != null }
        val totalValue = if (allValuesAvailable) holdings.sumOf { it.currentValue!! } else null
        val totalPnl = if (allValuesAvailable) holdings.sumOf { it.pnl ?: 0.0 } else null
        val totalPercentPnl = if (totalPnl != null && totalInvested > 0) {
            totalPnl / totalInvested * 100.0
        } else null

        _ui.update {
            it.copy(
                holdings = holdings.sortedByDescending { h -> h.totalInvested },
                totalInvested = totalInvested,
                totalValue = totalValue,
                totalPnl = totalPnl,
                totalPercentPnl = totalPercentPnl,
                isRefreshing = false
            )
        }
    }
}
