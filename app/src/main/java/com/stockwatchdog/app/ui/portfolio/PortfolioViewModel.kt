package com.stockwatchdog.app.ui.portfolio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stockwatchdog.app.data.api.MarketDataRepository
import com.stockwatchdog.app.data.db.PositionLotDao
import com.stockwatchdog.app.data.db.WatchlistDao
import com.stockwatchdog.app.data.db.entities.PositionLotEntity
import com.stockwatchdog.app.domain.DataResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HoldingRow(
    val symbol: String,
    val name: String?,
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
    val isRefreshing: Boolean = false
)

class PortfolioViewModel(
    private val watchlistDao: WatchlistDao,
    private val positionLotDao: PositionLotDao,
    private val repo: MarketDataRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(PortfolioUiState())
    val ui: StateFlow<PortfolioUiState> = _ui.asStateFlow()

    val lots: StateFlow<List<PositionLotEntity>> =
        positionLotDao.observeAll()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch { refresh() }
    }

    fun refresh() {
        viewModelScope.launch {
            _ui.update { it.copy(isRefreshing = true) }
            val allLots = positionLotDao.getAll()
            val lotsBySymbol = allLots.groupBy { it.symbol }

            if (lotsBySymbol.isEmpty()) {
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
                return@launch
            }

            val holdings = mutableListOf<HoldingRow>()

            for ((symbol, symbolLots) in lotsBySymbol) {
                val invested = symbolLots.sumOf { it.amountInvested }
                val totalQty = symbolLots.sumOf {
                    if (it.entryPrice > 0) it.amountInvested / it.entryPrice else 0.0
                }
                val watchItem = watchlistDao.getBySymbol(symbol)
                val name = watchItem?.name

                val currentPrice = when (val r = repo.getQuote(symbol, forceRefresh = true)) {
                    is DataResult.Success -> r.value.price
                    is DataResult.Error -> null
                }

                val currentValue = if (currentPrice != null && totalQty > 0) {
                    currentPrice * totalQty
                } else null

                val pnl = if (currentValue != null) currentValue - invested else null
                val percentPnl = if (pnl != null && invested > 0) pnl / invested * 100.0 else null

                holdings.add(
                    HoldingRow(
                        symbol = symbol,
                        name = name,
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
            val totalPnl = if (totalValue != null) totalValue - totalInvested else null
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
}
