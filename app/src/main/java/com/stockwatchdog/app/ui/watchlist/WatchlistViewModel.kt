package com.stockwatchdog.app.ui.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stockwatchdog.app.data.api.MarketDataRepository
import com.stockwatchdog.app.data.db.WatchlistDao
import com.stockwatchdog.app.data.db.entities.WatchlistItemEntity
import com.stockwatchdog.app.di.AppContainer
import com.stockwatchdog.app.domain.DataResult
import com.stockwatchdog.app.domain.Quote
import com.stockwatchdog.app.domain.SymbolMatch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WatchRow(
    val symbol: String,
    val name: String?,
    val quote: Quote?,
    val entryPrice: Double? = null,
    val quantity: Double? = null,
    val error: String? = null
)

data class WatchlistUiState(
    val rows: List<WatchRow> = emptyList(),
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val addSheetOpen: Boolean = false,
    val addQuery: String = "",
    val searchResults: List<SymbolMatch> = emptyList(),
    val searching: Boolean = false,
    val marketSummaryText: String? = null
)

class WatchlistViewModel(
    private val dao: WatchlistDao,
    private val repo: MarketDataRepository,
    private val container: AppContainer
) : ViewModel() {

    private val _ui = MutableStateFlow(WatchlistUiState())
    val ui: StateFlow<WatchlistUiState> = _ui.asStateFlow()

    private val quoteCache = MutableStateFlow<Map<String, WatchRow>>(emptyMap())

    val items: StateFlow<List<WatchlistItemEntity>> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            combine(items, quoteCache) { list, cache ->
                list.map { e ->
                    val base = cache[e.symbol]
                        ?: WatchRow(symbol = e.symbol, name = e.name, quote = null)
                    base.copy(
                        name = base.name ?: e.name,
                        entryPrice = e.entryPrice,
                        quantity = e.quantity
                    )
                }
            }.collect { rows ->
                _ui.update { it.copy(rows = rows) }
            }
        }
        // Kick off a refresh on first load so rows show prices quickly.
        viewModelScope.launch { refresh(force = false) }
    }

    fun refresh(force: Boolean = true) {
        viewModelScope.launch {
            val symbols = items.value.map { it.symbol }
            if (symbols.isEmpty()) {
                _ui.update { it.copy(isRefreshing = false) }
                return@launch
            }
            _ui.update { it.copy(isRefreshing = true, errorMessage = null) }
            val results = mutableMapOf<String, WatchRow>()
            // Sequential to respect rate limits on free tiers.
            for (sym in symbols) {
                when (val r = repo.getQuote(sym, forceRefresh = force)) {
                    is DataResult.Success -> results[sym] = WatchRow(
                        symbol = sym, name = r.value.name, quote = r.value
                    )
                    is DataResult.Error -> results[sym] = WatchRow(
                        symbol = sym, name = null, quote = null, error = r.message
                    )
                }
                quoteCache.update { it + results }
            }
            val summary = results.values.firstNotNullOfOrNull { it.quote?.marketIsOpen }?.let {
                if (it) "Market open" else "Market closed"
            }
            _ui.update { it.copy(isRefreshing = false, marketSummaryText = summary) }
        }
    }

    fun openAddSheet() = _ui.update { it.copy(addSheetOpen = true, addQuery = "", searchResults = emptyList()) }
    fun closeAddSheet() = _ui.update { it.copy(addSheetOpen = false, searchResults = emptyList()) }

    fun onAddQueryChange(q: String) {
        _ui.update { it.copy(addQuery = q) }
        searchJob?.cancel()
        if (q.isBlank() || q.length < 1) {
            _ui.update { it.copy(searchResults = emptyList(), searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            _ui.update { it.copy(searching = true) }
            kotlinx.coroutines.delay(300) // debounce
            when (val r = repo.search(q)) {
                is DataResult.Success -> _ui.update { it.copy(searchResults = r.value, searching = false) }
                is DataResult.Error -> _ui.update { it.copy(searchResults = emptyList(), searching = false, errorMessage = r.message) }
            }
        }
    }

    fun addSymbol(symbol: String, name: String? = null) {
        val sym = symbol.trim().uppercase()
        if (sym.isBlank()) return
        viewModelScope.launch {
            val existing = dao.getBySymbol(sym)
            if (existing == null) {
                val position = dao.count()
                dao.upsert(WatchlistItemEntity(symbol = sym, name = name, position = position))
            }
            _ui.update { it.copy(addSheetOpen = false, addQuery = "", searchResults = emptyList()) }
            refresh(force = true)
        }
    }

    fun remove(symbol: String) {
        viewModelScope.launch {
            dao.deleteBySymbol(symbol)
            quoteCache.update { it - symbol }
        }
    }

    fun move(fromIndex: Int, toIndex: Int) {
        val current = items.value.map { it.symbol }.toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices) return
        val moved = current.removeAt(fromIndex)
        current.add(toIndex, moved)
        viewModelScope.launch { dao.reorder(current) }
    }
}
