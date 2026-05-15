package com.stockwatchdog.app.ui.detail

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stockwatchdog.app.data.api.MarketDataRepository
import com.stockwatchdog.app.data.api.StockDetails
import com.stockwatchdog.app.data.api.StockDetailsRepository
import com.stockwatchdog.app.data.db.AlertDao
import com.stockwatchdog.app.data.db.PositionLotDao
import com.stockwatchdog.app.data.db.WatchlistDao
import com.stockwatchdog.app.data.db.entities.AlertEntity
import com.stockwatchdog.app.data.db.entities.AlertType
import com.stockwatchdog.app.data.db.entities.PositionLotEntity
import com.stockwatchdog.app.data.db.entities.WatchlistItemEntity
import com.stockwatchdog.app.data.prefs.SettingsRepository
import com.stockwatchdog.app.domain.ChartRange
import com.stockwatchdog.app.domain.DataResult
import com.stockwatchdog.app.domain.PositionCalculator
import com.stockwatchdog.app.domain.PricePoint
import com.stockwatchdog.app.domain.Quote
import com.stockwatchdog.app.work.AlertWorkScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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
    val details: StockDetails? = null,
    val detailsLoading: Boolean = false,
    val detailsError: String? = null,
    val inWatchlist: Boolean = false,
    /** All recorded buys for this ticker, oldest first. */
    val lots: List<PositionLotEntity> = emptyList(),
    /** Weighted-average entry across all lots. Used for "% vs entry" alerts. */
    val avgEntryPrice: Double? = null,
    val platformFeePercent: Double = 0.0,
    val createAlertOpen: Boolean = false,
    val newAlertType: AlertType = AlertType.PRICE_ABOVE,
    val newAlertThreshold: String = "",
    val newAlertNotes: String = "",
    val newAlertAutoDisable: Boolean = false,
    val newAlertMarketHoursOnly: Boolean? = null,
    /** Add/edit lot dialog state. `editingLotId == null` means adding a new lot. */
    val lotDialogOpen: Boolean = false,
    val lotDialogEditingId: Long? = null,
    val lotDialogPriceDraft: String = "",
    val lotDialogAmountDraft: String = "",
    val lotDialogPlatformDraft: String = "",
    /** Confirm-delete: id of the lot waiting on user confirmation, or null. */
    val lotDeleteConfirmId: Long? = null,
    /** Confirm-delete alert: id of the alert waiting on user confirmation. */
    val alertDeleteConfirmId: Long? = null
)

class TickerDetailViewModel(
    private val symbol: String,
    private val repo: MarketDataRepository,
    private val detailsRepo: StockDetailsRepository,
    private val watchlistDao: WatchlistDao,
    private val alertDao: AlertDao,
    private val positionLotDao: PositionLotDao,
    private val settingsRepository: SettingsRepository,
    private val appContext: Context
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
                _ui.update { it.copy(inWatchlist = entity != null) }
            }
        }
        viewModelScope.launch {
            positionLotDao.observeBySymbol(symbol).collect { lots ->
                _ui.update { it.copy(lots = lots, avgEntryPrice = weightedAvgEntry(lots)) }
            }
        }
        viewModelScope.launch {
            settingsRepository.settings.collectLatest { settings ->
                _ui.update { it.copy(platformFeePercent = settings.platformFeePercent) }
            }
        }
    }

    private fun weightedAvgEntry(lots: List<PositionLotEntity>): Double? {
        val invested = lots.sumOf { it.amountInvested }
        val qty = lots.sumOf {
            if (it.entryPrice > 0) it.amountInvested / it.entryPrice else 0.0
        }
        return if (qty > 0) invested / qty else null
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
        viewModelScope.launch { loadDetails(force) }
    }

    private suspend fun loadDetails(force: Boolean) {
        _ui.update { it.copy(detailsLoading = true, detailsError = null) }
        val details = runCatching {
            detailsRepo.get(symbol, forceRefresh = force, includeFmp = true)
        }.getOrNull()
        _ui.update {
            it.copy(
                details = details,
                detailsLoading = false,
                detailsError = if (details == null) {
                    "Financial data is unavailable for this ticker right now."
                } else null
            )
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
            newAlertThreshold = it.quote?.price?.let { p -> "%.2f".format(p) } ?: "",
            newAlertNotes = "",
            newAlertAutoDisable = false,
            newAlertMarketHoursOnly = null
        )
    }
    fun closeCreateAlert() = _ui.update {
        it.copy(
            createAlertOpen = false,
            newAlertNotes = "",
            newAlertAutoDisable = false,
            newAlertMarketHoursOnly = null
        )
    }
    fun onAlertTypeChange(t: AlertType) = _ui.update {
        it.copy(
            newAlertType = t,
            newAlertThreshold = defaultThresholdFor(t, it.quote?.price)
        )
    }
    fun onAlertThresholdChange(v: String) = _ui.update { it.copy(newAlertThreshold = v) }
    fun onAlertNotesChange(v: String) = _ui.update { it.copy(newAlertNotes = v) }
    fun onAlertAutoDisableChange(v: Boolean) = _ui.update { it.copy(newAlertAutoDisable = v) }
    fun onAlertMarketHoursOnlyChange(v: Boolean?) = _ui.update { it.copy(newAlertMarketHoursOnly = v) }

    fun saveAlert() {
        val s = _ui.value
        val threshold = parseAlertThreshold(s.newAlertType, s.newAlertThreshold) ?: return
        viewModelScope.launch {
            val initialState = _ui.value.quote?.let { q ->
                val entry = _ui.value.avgEntryPrice
                when (s.newAlertType) {
                    AlertType.PRICE_ABOVE -> q.price > threshold
                    AlertType.PRICE_BELOW -> q.price < threshold
                    AlertType.PERCENT_CHANGE_DAY -> false
                    AlertType.PERCENT_ABOVE_ENTRY -> {
                        if (entry != null && entry > 0) {
                            val pct = PositionCalculator.netPercentVsEntry(
                                currentPrice = q.price,
                                entryPrice = entry,
                                platformFeePercent = s.platformFeePercent
                            )
                            pct >= threshold
                        } else false
                    }
                    AlertType.PERCENT_BELOW_ENTRY -> {
                        if (entry != null && entry > 0) {
                            val pct = PositionCalculator.netPercentVsEntry(
                                currentPrice = q.price,
                                entryPrice = entry,
                                platformFeePercent = s.platformFeePercent
                            )
                            pct <= -threshold
                        } else false
                    }
                    // Detail-screen "New alert" only exposes the five types
                    // above; richer types (earnings, 52w, MA200, volume, target)
                    // are created from the Alerts page. Default to "not yet
                    // crossed" so the worker fires on the first observed match.
                    AlertType.EARNINGS_REMINDER,
                    AlertType.FIFTY_TWO_WEEK_HIGH,
                    AlertType.FIFTY_TWO_WEEK_LOW,
                    AlertType.MA200_CROSS_UP,
                    AlertType.MA200_CROSS_DOWN,
                    AlertType.VOLUME_SPIKE,
                    AlertType.ANALYST_TARGET_REACH -> false
                }
            }
            alertDao.insert(
                AlertEntity(
                    symbol = symbol,
                    type = s.newAlertType,
                    threshold = threshold,
                    enabled = true,
                    lastCrossingState = initialState,
                    notes = s.newAlertNotes.trim().ifBlank { null },
                    autoDisableAfterFire = s.newAlertAutoDisable,
                    marketHoursOnly = s.newAlertMarketHoursOnly
                )
            )
            _ui.update {
                it.copy(
                    createAlertOpen = false,
                    newAlertThreshold = "",
                    newAlertNotes = "",
                    newAlertAutoDisable = false,
                    newAlertMarketHoursOnly = null
                )
            }
            AlertWorkScheduler.runNow(appContext)
        }
    }

    fun toggleAlert(id: Long, enabled: Boolean) =
        viewModelScope.launch {
            alertDao.setEnabled(id, enabled)
            if (enabled) AlertWorkScheduler.runNow(appContext)
        }

    fun confirmDeleteAlert(id: Long) = _ui.update { it.copy(alertDeleteConfirmId = id) }
    fun cancelDeleteAlert() = _ui.update { it.copy(alertDeleteConfirmId = null) }
    fun deleteAlert(id: Long) {
        viewModelScope.launch {
            alertDao.delete(id)
            _ui.update { it.copy(alertDeleteConfirmId = null) }
        }
    }

    /** Shortcut: create a take-profit alert (gain vs entry %) from a position card. */
    fun createTakeProfitAlert(percent: Double) {
        if (percent <= 0) return
        viewModelScope.launch {
            val state = _ui.value
            val entry = state.avgEntryPrice ?: return@launch
            val q = state.quote
            val initialState = if (q != null && entry > 0) {
                val pct = PositionCalculator.netPercentVsEntry(
                    currentPrice = q.price,
                    entryPrice = entry,
                    platformFeePercent = state.platformFeePercent
                )
                pct >= percent
            } else false
            alertDao.insert(
                AlertEntity(
                    symbol = symbol,
                    type = AlertType.PERCENT_ABOVE_ENTRY,
                    threshold = percent,
                    enabled = true,
                    lastCrossingState = initialState
                )
            )
            AlertWorkScheduler.runNow(appContext)
        }
    }

    /** Shortcut: create a stop-loss alert (loss vs entry %) from a position card. */
    fun createStopLossAlert(percent: Double) {
        if (percent <= 0) return
        viewModelScope.launch {
            val state = _ui.value
            val entry = state.avgEntryPrice ?: return@launch
            val q = state.quote
            val initialState = if (q != null && entry > 0) {
                val pct = PositionCalculator.netPercentVsEntry(
                    currentPrice = q.price,
                    entryPrice = entry,
                    platformFeePercent = state.platformFeePercent
                )
                pct <= -percent
            } else false
            alertDao.insert(
                AlertEntity(
                    symbol = symbol,
                    type = AlertType.PERCENT_BELOW_ENTRY,
                    threshold = percent,
                    enabled = true,
                    lastCrossingState = initialState
                )
            )
            AlertWorkScheduler.runNow(appContext)
        }
    }

    // ---------- Position lots (multi-entry) ----------

    /** Open the dialog for adding a brand-new lot. */
    fun openAddLot() = _ui.update {
        it.copy(
            lotDialogOpen = true,
            lotDialogEditingId = null,
            lotDialogPriceDraft = "",
            lotDialogAmountDraft = "",
            lotDialogPlatformDraft = ""
        )
    }

    /** Open the dialog pre-filled with an existing lot's values for editing. */
    fun openEditLot(lotId: Long) {
        val lot = _ui.value.lots.firstOrNull { it.id == lotId } ?: return
        _ui.update {
            it.copy(
                lotDialogOpen = true,
                lotDialogEditingId = lotId,
                lotDialogPriceDraft = trimmed(lot.entryPrice),
                lotDialogAmountDraft = trimmed(lot.amountInvested),
                lotDialogPlatformDraft = lot.platform ?: ""
            )
        }
    }

    fun closeLotDialog() = _ui.update {
        it.copy(
            lotDialogOpen = false,
            lotDialogEditingId = null,
            lotDialogPriceDraft = "",
            lotDialogAmountDraft = "",
            lotDialogPlatformDraft = ""
        )
    }

    fun onLotPriceDraftChange(v: String) = _ui.update { it.copy(lotDialogPriceDraft = v) }
    fun onLotAmountDraftChange(v: String) = _ui.update { it.copy(lotDialogAmountDraft = v) }
    fun onLotPlatformDraftChange(v: String) = _ui.update { it.copy(lotDialogPlatformDraft = v) }

    /** Create or update the lot currently in the dialog. */
    fun saveLot() {
        val s = _ui.value
        val entry = parseDecimal(s.lotDialogPriceDraft) ?: return
        val amount = parseDecimal(s.lotDialogAmountDraft) ?: return
        if (entry <= 0.0 || amount <= 0.0) return
        viewModelScope.launch {
            // Ensure a watchlist row exists so the FK on position_lots resolves.
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
            val platform = s.lotDialogPlatformDraft.trim().ifBlank { null }
            val editingId = s.lotDialogEditingId
            if (editingId == null) {
                positionLotDao.insert(
                    PositionLotEntity(
                        symbol = symbol,
                        entryPrice = entry,
                        amountInvested = amount,
                        platform = platform
                    )
                )
            } else {
                val existing = s.lots.firstOrNull { it.id == editingId }
                if (existing != null) {
                    positionLotDao.update(
                        existing.copy(entryPrice = entry, amountInvested = amount, platform = platform)
                    )
                }
            }
            _ui.update {
                it.copy(
                    lotDialogOpen = false,
                    lotDialogEditingId = null,
                    lotDialogPriceDraft = "",
                    lotDialogAmountDraft = "",
                    lotDialogPlatformDraft = ""
                )
            }
        }
    }

    /** Ask the UI to show a confirm dialog before actually deleting a lot. */
    fun confirmDeleteLot(lotId: Long) = _ui.update { it.copy(lotDeleteConfirmId = lotId) }
    fun cancelDeleteLot() = _ui.update { it.copy(lotDeleteConfirmId = null) }

    /** Proceed with deletion after the user confirmed. */
    fun deleteLot(lotId: Long) {
        viewModelScope.launch {
            positionLotDao.deleteById(lotId)
            _ui.update { it.copy(lotDeleteConfirmId = null) }
        }
    }

    private fun parseDecimal(raw: String): Double? =
        raw.replace(",", ".").trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()

    private fun parseAlertThreshold(type: AlertType, raw: String): Double? =
        when (type) {
            AlertType.FIFTY_TWO_WEEK_HIGH,
            AlertType.FIFTY_TWO_WEEK_LOW,
            AlertType.MA200_CROSS_UP,
            AlertType.MA200_CROSS_DOWN,
            AlertType.ANALYST_TARGET_REACH -> 0.0
            else -> parseDecimal(raw)?.takeIf { it > 0.0 }
        }

    private fun defaultThresholdFor(type: AlertType, quotePrice: Double?): String =
        when (type) {
            AlertType.PRICE_ABOVE,
            AlertType.PRICE_BELOW -> quotePrice?.let { "%.2f".format(it) } ?: ""
            AlertType.PERCENT_CHANGE_DAY -> "3"
            AlertType.PERCENT_ABOVE_ENTRY -> "10"
            AlertType.PERCENT_BELOW_ENTRY -> "5"
            AlertType.EARNINGS_REMINDER -> "1"
            AlertType.VOLUME_SPIKE -> "2"
            AlertType.FIFTY_TWO_WEEK_HIGH,
            AlertType.FIFTY_TWO_WEEK_LOW,
            AlertType.MA200_CROSS_UP,
            AlertType.MA200_CROSS_DOWN,
            AlertType.ANALYST_TARGET_REACH -> ""
        }

    private fun trimmed(v: Double): String =
        if (v % 1.0 == 0.0) "%.0f".format(v) else v.toString()
}
