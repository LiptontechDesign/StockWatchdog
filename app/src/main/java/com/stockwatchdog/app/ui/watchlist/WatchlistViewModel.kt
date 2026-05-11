package com.stockwatchdog.app.ui.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stockwatchdog.app.data.api.MarketDataRepository
import com.stockwatchdog.app.data.api.StockDetails
import com.stockwatchdog.app.data.api.StockDetailsRepository
import com.stockwatchdog.app.data.db.PositionLotDao
import com.stockwatchdog.app.data.db.WatchlistDao
import com.stockwatchdog.app.data.db.entities.PositionLotEntity
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
    /** Weighted-average entry across this ticker's lots, or legacy single entry. */
    val entryPrice: Double? = null,
    /** Derived quantity (sum of amountInvested / entryPrice across lots). */
    val quantity: Double? = null,
    val details: StockDetails? = null,
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
    val marketSummaryText: String? = null,
    val marketSession: MarketSessionSummary? = null,
    val platformFeePercent: Double = 0.0,
    /** Symbol waiting on user confirmation before deletion. */
    val confirmDeleteSymbol: String? = null,
    /** Non-null while the "Undo" snackbar is visible after a delete. */
    val undoDeleteEntity: WatchlistItemEntity? = null
)

class WatchlistViewModel(
    private val dao: WatchlistDao,
    private val positionLotDao: PositionLotDao,
    private val repo: MarketDataRepository,
    private val detailsRepo: StockDetailsRepository,
    private val container: AppContainer
) : ViewModel() {

    private val _ui = MutableStateFlow(WatchlistUiState())
    val ui: StateFlow<WatchlistUiState> = _ui.asStateFlow()

    private val quoteCache = MutableStateFlow<Map<String, WatchRow>>(emptyMap())

    val items: StateFlow<List<WatchlistItemEntity>> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val lots: StateFlow<List<PositionLotEntity>> =
        positionLotDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var searchJob: Job? = null

    init {
        val initialSummary = MarketSessionClock.summary()
        _ui.update {
            it.copy(
                marketSummaryText = initialSummary.asPlainText(),
                marketSession = initialSummary
            )
        }
        viewModelScope.launch {
            combine(items, quoteCache, lots) { list, cache, allLots ->
                val lotsBySymbol = allLots.groupBy { it.symbol }
                list.map { e ->
                    val base = cache[e.symbol]
                        ?: WatchRow(symbol = e.symbol, name = e.name, quote = null)
                    val symbolLots = lotsBySymbol[e.symbol].orEmpty()
                    val (avgEntry, totalQty) = if (symbolLots.isNotEmpty()) {
                        val invested = symbolLots.sumOf { it.amountInvested }
                        val qty = symbolLots.sumOf {
                            if (it.entryPrice > 0) it.amountInvested / it.entryPrice else 0.0
                        }
                        val avg = if (qty > 0) invested / qty else null
                        avg to qty.takeIf { it > 0 }
                    } else {
                        e.entryPrice to e.quantity
                    }
                    base.copy(
                        name = base.name ?: e.name,
                        entryPrice = avgEntry,
                        quantity = totalQty
                    )
                }
            }.collect { rows ->
                _ui.update { it.copy(rows = rows) }
            }
        }
        viewModelScope.launch {
            container.settingsRepository.settings.collect { settings ->
                _ui.update { it.copy(platformFeePercent = settings.platformFeePercent) }
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
                        symbol = sym,
                        name = r.value.name,
                        quote = r.value,
                        details = quoteCache.value[sym]?.details
                    )
                    is DataResult.Error -> results[sym] = WatchRow(
                        symbol = sym,
                        name = null,
                        quote = null,
                        details = quoteCache.value[sym]?.details,
                        error = r.message
                    )
                }
                quoteCache.update { it + results }
            }
            val summary = MarketSessionClock.summary()
            _ui.update {
                it.copy(
                    isRefreshing = false,
                    marketSummaryText = summary.asPlainText(),
                    marketSession = summary
                )
            }
            loadDetails(symbols, forceRefresh = force)
        }
    }

    private fun loadDetails(symbols: List<String>, forceRefresh: Boolean) {
        viewModelScope.launch {
            for (sym in symbols) {
                val details = detailsRepo.get(sym, forceRefresh = forceRefresh) ?: continue
                quoteCache.update { cache ->
                    val current = cache[sym] ?: WatchRow(symbol = sym, name = null, quote = null)
                    cache + (sym to current.copy(details = details))
                }
            }
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

    /** Step 1: show confirm dialog. */
    fun confirmRemove(symbol: String) = _ui.update { it.copy(confirmDeleteSymbol = symbol) }
    fun cancelRemove() = _ui.update { it.copy(confirmDeleteSymbol = null) }

    /** Step 2: actually delete after user confirmed, then show undo snackbar. */
    fun remove(symbol: String) {
        viewModelScope.launch {
            val entity = dao.getBySymbol(symbol)
            dao.deleteBySymbol(symbol)
            quoteCache.update { it - symbol }
            _ui.update {
                it.copy(
                    confirmDeleteSymbol = null,
                    undoDeleteEntity = entity
                )
            }
        }
    }

    /** Re-insert the entity that was just deleted (undo). */
    fun undoRemove() {
        val entity = _ui.value.undoDeleteEntity ?: return
        viewModelScope.launch {
            dao.upsert(entity)
            _ui.update { it.copy(undoDeleteEntity = null) }
            refresh(force = true)
        }
    }

    fun dismissUndoSnackbar() = _ui.update { it.copy(undoDeleteEntity = null) }

    fun move(fromIndex: Int, toIndex: Int) {
        val current = items.value.map { it.symbol }.toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices) return
        val moved = current.removeAt(fromIndex)
        current.add(toIndex, moved)
        viewModelScope.launch { dao.reorder(current) }
    }
}

data class MarketSessionSummary(
    val isOpen: Boolean,
    val statusLabel: String,
    val actionLabel: String,
    val duration: String,
    val nairobiTime: String
) {
    fun asPlainText(): String = "$statusLabel - $actionLabel in $duration ($nairobiTime Nairobi)"
}

private object MarketSessionClock {
    private val nairobiZone = java.time.ZoneId.of("Africa/Nairobi")
    private val marketZone = java.time.ZoneId.of("America/New_York")
    private val openTime = java.time.LocalTime.of(9, 30)
    private val closeTime = java.time.LocalTime.of(16, 0)
    private val timeFmt = java.time.format.DateTimeFormatter.ofPattern("EEE HH:mm")

    fun summary(now: java.time.Instant = java.time.Instant.now()): MarketSessionSummary {
        val marketNow = now.atZone(marketZone)
        val date = marketNow.toLocalDate()
        val open = date.atTime(openTime).atZone(marketZone)
        val close = date.atTime(closeTime).atZone(marketZone)

        return if (isTradingDay(date) && marketNow >= open && marketNow < close) {
            val closeNairobi = close.withZoneSameInstant(nairobiZone)
            MarketSessionSummary(
                isOpen = true,
                statusLabel = "MARKET OPEN",
                actionLabel = "closes",
                duration = formatDuration(java.time.Duration.between(marketNow, close)),
                nairobiTime = closeNairobi.format(timeFmt)
            )
        } else {
            val nextOpen = nextOpenAfter(marketNow)
            val nextOpenNairobi = nextOpen.withZoneSameInstant(nairobiZone)
            MarketSessionSummary(
                isOpen = false,
                statusLabel = "MARKET CLOSED",
                actionLabel = "opens",
                duration = formatDuration(java.time.Duration.between(marketNow, nextOpen)),
                nairobiTime = nextOpenNairobi.format(timeFmt)
            )
        }
    }

    private fun nextOpenAfter(marketNow: java.time.ZonedDateTime): java.time.ZonedDateTime {
        var date = marketNow.toLocalDate()
        val todayOpen = date.atTime(openTime).atZone(marketZone)
        if (isTradingDay(date) && marketNow < todayOpen) return todayOpen
        do {
            date = date.plusDays(1)
        } while (!isTradingDay(date))
        return date.atTime(openTime).atZone(marketZone)
    }

    private fun isTradingDay(date: java.time.LocalDate): Boolean =
        date.dayOfWeek != java.time.DayOfWeek.SATURDAY &&
            date.dayOfWeek != java.time.DayOfWeek.SUNDAY

    private fun formatDuration(duration: java.time.Duration): String {
        val totalMinutes = duration.toMinutes().coerceAtLeast(0)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            else -> "${minutes}m"
        }
    }
}
