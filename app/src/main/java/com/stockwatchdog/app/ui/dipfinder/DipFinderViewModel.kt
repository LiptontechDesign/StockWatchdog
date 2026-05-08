package com.stockwatchdog.app.ui.dipfinder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stockwatchdog.app.data.api.DipFinderRepository
import com.stockwatchdog.app.data.api.toAnalysis
import com.stockwatchdog.app.domain.DipAnalysis
import com.stockwatchdog.app.domain.DipLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for the Dip Finder screen. Two sections, both rendered from the
 * single cached `dip_finder_results` table.
 *
 *  - [autoRows] are the curated tickers in [DipFinderRepository.autoUniverse].
 *  - [watchlistRows] are the user's manually-added tickers.
 *
 * Each row is a fully-resolved [DipAnalysis] so the screen renders the
 * pre-computed plain-English label/reason/score with zero extra logic.
 */
data class DipFinderUiState(
    val autoRows: List<DipAnalysis> = emptyList(),
    val watchlistRows: List<DipAnalysis> = emptyList(),
    val watchlistSymbols: List<String> = emptyList(),
    val isRefreshing: Boolean = false,
    val isAddingSymbol: Boolean = false,
    val message: String? = null,
    val lastRefreshedAtMs: Long? = null,
    val tickerInput: String = ""
)

class DipFinderViewModel(
    private val repo: DipFinderRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(DipFinderUiState())
    val ui: StateFlow<DipFinderUiState> = _ui.asStateFlow()

    init {
        // Re-render whenever cached results or the watchlist changes.
        viewModelScope.launch {
            combine(
                repo.observeAllResults(),
                repo.observeWatchlist()
            ) { results, watchSymbols -> results to watchSymbols }
                .collect { (results, watchSymbols) ->
                    val byKey = results.associateBy { it.symbol }
                    val auto = repo.autoUniverse.mapNotNull { sym ->
                        byKey[sym]?.toAnalysis()
                    }.sortedWith(displayOrder)

                    val watch = watchSymbols.mapNotNull { sym ->
                        byKey[sym]?.toAnalysis()
                    }.sortedWith(displayOrder)

                    val mostRecent = results.maxOfOrNull { it.computedAtMillis }

                    _ui.update {
                        it.copy(
                            autoRows = auto,
                            watchlistRows = watch,
                            watchlistSymbols = watchSymbols,
                            lastRefreshedAtMs = mostRecent
                        )
                    }
                }
        }

        // Kick off a first refresh on cold start so the cache is populated.
        viewModelScope.launch { refreshAll(forceRefresh = false) }
    }

    fun onTickerInputChange(v: String) {
        _ui.update { it.copy(tickerInput = v.uppercase()) }
    }

    /** Add the current input to the watchlist and analyse it immediately. */
    fun addCurrentInput() {
        val symbol = _ui.value.tickerInput.trim().uppercase()
        if (symbol.isBlank()) return
        viewModelScope.launch {
            _ui.update { it.copy(isAddingSymbol = true, message = null) }
            try {
                repo.addToWatchlist(symbol)
                repo.refreshSymbol(symbol, forceRefresh = true)
                _ui.update {
                    it.copy(
                        isAddingSymbol = false,
                        tickerInput = "",
                        message = "$symbol added"
                    )
                }
            } catch (t: Throwable) {
                _ui.update {
                    it.copy(
                        isAddingSymbol = false,
                        message = "Could not add $symbol: ${t.message ?: "unknown error"}"
                    )
                }
            }
        }
    }

    fun removeFromWatchlist(symbol: String) {
        viewModelScope.launch {
            repo.removeFromWatchlist(symbol)
            _ui.update { it.copy(message = "$symbol removed") }
        }
    }

    fun refreshSymbol(symbol: String) {
        viewModelScope.launch {
            _ui.update { it.copy(isRefreshing = true) }
            runCatching { repo.refreshSymbol(symbol, forceRefresh = true) }
            _ui.update { it.copy(isRefreshing = false) }
        }
    }

    fun refreshAll(forceRefresh: Boolean = true) {
        viewModelScope.launch {
            _ui.update { it.copy(isRefreshing = true, message = null) }
            runCatching { repo.refreshAll(forceRefresh = forceRefresh) }
                .onFailure { t ->
                    _ui.update { it.copy(message = "Refresh failed: ${t.message ?: "unknown"}") }
                }
            _ui.update { it.copy(isRefreshing = false) }
        }
    }

    fun consumeMessage() {
        _ui.update { it.copy(message = null) }
    }

    companion object {
        /**
         * Display order: STRONG_DIP first, then WATCH/RISKY, then VALUE_TRAP,
         * then NOT_IN_DIP last. Tie-breaker is highest score.
         */
        private val displayOrder: Comparator<DipAnalysis> = compareBy<DipAnalysis> { row ->
            when (row.label) {
                DipLabel.STRONG_DIP -> 0
                DipLabel.WATCH_DIP -> 1
                DipLabel.RISKY_DIP -> 2
                DipLabel.VALUE_TRAP -> 3
                DipLabel.NOT_IN_DIP -> 4
            }
        }.thenByDescending { it.score }
            .thenBy { it.symbol }
    }
}

