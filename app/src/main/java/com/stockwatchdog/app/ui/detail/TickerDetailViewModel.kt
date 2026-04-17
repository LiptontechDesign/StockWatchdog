package com.stockwatchdog.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stockwatchdog.app.data.api.MarketDataRepository
import com.stockwatchdog.app.data.db.AlertDao
import com.stockwatchdog.app.data.db.WatchlistDao
import com.stockwatchdog.app.data.db.entities.AlertEntity
import com.stockwatchdog.app.data.db.entities.AlertType
import com.stockwatchdog.app.data.db.entities.WatchlistItemEntity
import com.stockwatchdog.app.domain.ChartRange
import com.stockwatchdog.app.domain.DataResult
import com.stockwatchdog.app.domain.PricePoint
import com.stockwatchdog.app.domain.Quote
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailUiState(
    val symbol: String,
    val quote: Quote? = null,
    val quoteLoading: Boolean = true,
    val quoteError: String? = null,
    val range: ChartRange = ChartRange.ONE_DAY,
    val points: List<PricePoint> = emptyList(),
    val chartLoading: Boolean = false,
    val chartError: String? = null,
    val inWatchlist: Boolean = false,
    val entryPrice: Double? = null,
    val quantity: Double? = null,
    val notes: String? = null,
    val createAlertOpen: Boolean = false,
    val newAlertType: AlertType = AlertType.PRICE_ABOVE,
    val newAlertThreshold: String = "",
    val editPositionOpen: Boolean = false,
    val entryPriceDraft: String = "",
    val quantityDraft: String = "",
    val notesDraft: String = ""
)

class TickerDetailViewModel(
    private val symbol: String,
    private val repo: MarketDataRepository,
    private val watchlistDao: WatchlistDao,
    private val alertDao: AlertDao
) : ViewModel() {

    private val _ui = MutableStateFlow(DetailUiState(symbol = symbol))
    val ui: StateFlow<DetailUiState> = _ui.asStateFlow()

    val alerts: StateFlow<List<AlertEntity>> =
        alertDao.observeBySymbol(symbol)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch { refresh(force = false) }
        viewModelScope.launch { loadChart(ChartRange.ONE_DAY) }
        viewModelScope.launch {
            watchlistDao.observeBySymbol(symbol).collect { entity ->
                _ui.update {
                    it.copy(
                        inWatchlist = entity != null,
                        entryPrice = entity?.entryPrice,
                        quantity = entity?.quantity,
                        notes = entity?.notes
                    )
                }
            }
        }
    }

    fun refresh(force: Boolean = true) {
        viewModelScope.launch {
            _ui.update { it.copy(quoteLoading = true, quoteError = null) }
            when (val r = repo.getQuote(symbol, forceRefresh = force)) {
                is DataResult.Success -> _ui.update {
                    it.copy(quote = r.value, quoteLoading = false, quoteError = null)
                }
                is DataResult.Error -> _ui.update {
                    it.copy(quoteLoading = false, quoteError = r.message)
                }
            }
        }
    }

    fun selectRange(range: ChartRange) {
        if (_ui.value.range == range) return
        _ui.update { it.copy(range = range) }
        viewModelScope.launch { loadChart(range) }
    }

    private suspend fun loadChart(range: ChartRange) {
        _ui.update { it.copy(chartLoading = true, chartError = null) }
        when (val r = repo.getSeries(symbol, range)) {
            is DataResult.Success -> _ui.update {
                it.copy(points = r.value, chartLoading = false, chartError = null)
            }
            is DataResult.Error -> _ui.update {
                it.copy(chartLoading = false, chartError = r.message)
            }
        }
    }

    fun toggleWatchlist() {
        viewModelScope.launch {
            val existing = watchlistDao.getBySymbol(symbol)
            if (existing == null) {
                val position = watchlistDao.count()
                watchlistDao.upsert(
                    WatchlistItemEntity(
                        symbol = symbol,
                        name = _ui.value.quote?.name,
                        position = position
                    )
                )
                _ui.update { it.copy(inWatchlist = true) }
            } else {
                watchlistDao.deleteBySymbol(symbol)
                _ui.update { it.copy(inWatchlist = false) }
            }
        }
    }

    fun openCreateAlert() = _ui.update {
        it.copy(
            createAlertOpen = true,
            newAlertType = AlertType.PRICE_ABOVE,
            newAlertThreshold = it.quote?.price?.let { p -> "%.2f".format(p) } ?: ""
        )
    }
    fun closeCreateAlert() = _ui.update { it.copy(createAlertOpen = false) }
    fun onAlertTypeChange(t: AlertType) = _ui.update { it.copy(newAlertType = t) }
    fun onAlertThresholdChange(v: String) = _ui.update { it.copy(newAlertThreshold = v) }

    fun saveAlert() {
        val s = _ui.value
        val threshold = s.newAlertThreshold.replace(",", ".").toDoubleOrNull() ?: return
        viewModelScope.launch {
            val initialState = _ui.value.quote?.let { q ->
                val entry = _ui.value.entryPrice
                when (s.newAlertType) {
                    AlertType.PRICE_ABOVE -> q.price > threshold
                    AlertType.PRICE_BELOW -> q.price < threshold
                    AlertType.PERCENT_CHANGE_DAY -> false
                    AlertType.PERCENT_ABOVE_ENTRY -> {
                        if (entry != null && entry > 0) {
                            val pct = (q.price - entry) / entry * 100.0
                            pct >= threshold
                        } else false
                    }
                    AlertType.PERCENT_BELOW_ENTRY -> {
                        if (entry != null && entry > 0) {
                            val pct = (q.price - entry) / entry * 100.0
                            pct <= -threshold
                        } else false
                    }
                }
            }
            alertDao.insert(
                AlertEntity(
                    symbol = symbol,
                    type = s.newAlertType,
                    threshold = threshold,
                    enabled = true,
                    lastCrossingState = initialState
                )
            )
            _ui.update { it.copy(createAlertOpen = false, newAlertThreshold = "") }
        }
    }

    fun toggleAlert(id: Long, enabled: Boolean) =
        viewModelScope.launch { alertDao.setEnabled(id, enabled) }

    fun deleteAlert(id: Long) = viewModelScope.launch { alertDao.delete(id) }

    // ---------- Position (entry price / quantity / notes) ----------

    fun openEditPosition() = _ui.update {
        it.copy(
            editPositionOpen = true,
            entryPriceDraft = it.entryPrice?.let { v -> trimmed(v) } ?: "",
            quantityDraft = it.quantity?.let { v -> trimmed(v) } ?: "",
            notesDraft = it.notes.orEmpty()
        )
    }

    fun closeEditPosition() = _ui.update { it.copy(editPositionOpen = false) }

    fun onEntryPriceDraftChange(v: String) = _ui.update { it.copy(entryPriceDraft = v) }
    fun onQuantityDraftChange(v: String) = _ui.update { it.copy(quantityDraft = v) }
    fun onNotesDraftChange(v: String) = _ui.update { it.copy(notesDraft = v) }

    fun savePosition() {
        val s = _ui.value
        val entry = parseDecimal(s.entryPriceDraft)
        val qty = parseDecimal(s.quantityDraft)
        val notes = s.notesDraft.trim().ifBlank { null }
        viewModelScope.launch {
            // If this symbol isn't in the watchlist yet, add it first so the
            // position info has a row to attach to.
            if (watchlistDao.getBySymbol(symbol) == null) {
                val position = watchlistDao.count()
                watchlistDao.upsert(
                    WatchlistItemEntity(
                        symbol = symbol,
                        name = _ui.value.quote?.name,
                        position = position
                    )
                )
            }
            watchlistDao.updateEntryInfo(symbol, entry, qty, notes)
            _ui.update { it.copy(editPositionOpen = false) }
        }
    }

    fun clearPosition() {
        viewModelScope.launch {
            watchlistDao.updateEntryInfo(symbol, null, null, null)
            _ui.update {
                it.copy(
                    editPositionOpen = false,
                    entryPriceDraft = "",
                    quantityDraft = "",
                    notesDraft = ""
                )
            }
        }
    }

    private fun parseDecimal(raw: String): Double? =
        raw.replace(",", ".").trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()

    private fun trimmed(v: Double): String =
        if (v % 1.0 == 0.0) "%.0f".format(v) else v.toString()
}
