package com.stockwatchdog.app.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stockwatchdog.app.data.api.MarketDataRepository
import com.stockwatchdog.app.data.db.AlertDao
import com.stockwatchdog.app.data.db.AlertEventDao
import com.stockwatchdog.app.data.db.PriceCacheDao
import com.stockwatchdog.app.data.db.entities.AlertEntity
import com.stockwatchdog.app.data.db.entities.AlertEventEntity
import com.stockwatchdog.app.data.db.entities.AlertType
import com.stockwatchdog.app.domain.DataResult
import com.stockwatchdog.app.domain.Quote
import com.stockwatchdog.app.domain.SymbolMatch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Snapshot of one alert decorated with the freshest known price (from
 * the price cache or the alert's own last-seen price) so the UI can show
 * "distance to trigger" without having to re-fetch.
 */
data class AlertRow(
    val entity: AlertEntity,
    val livePrice: Double?,
    /**
     * Signed % distance from the trigger. Positive = needs to move up to fire,
     * negative = needs to move down. `null` for alert types where the
     * concept doesn't apply (e.g. earnings, volume spike).
     */
    val distancePct: Double?
)

data class AlertsUiState(
    val rows: List<AlertRow> = emptyList(),
    val events: List<AlertEventEntity> = emptyList(),
    val confirmDeleteId: Long? = null,
    val undoDeleteEntity: AlertEntity? = null,
    /** Open the edit/create dialog when non-null. `editing == null` means "create". */
    val dialogOpen: Boolean = false,
    val dialogEditing: AlertEntity? = null,
    val dialogSymbol: String = "",
    val dialogType: AlertType = AlertType.PRICE_ABOVE,
    val dialogThreshold: String = "",
    val dialogNotes: String = "",
    val dialogAutoDisable: Boolean = false,
    val dialogMarketHoursOnly: Boolean? = null,
    val dialogCompanyName: String? = null,
    val dialogCurrentPrice: Double? = null,
    val dialogPercentChange: Double? = null,
    val dialogCurrency: String? = null,
    /** Symbol search state for the FAB create flow. */
    val pickerOpen: Boolean = false,
    val pickerQuery: String = "",
    val pickerResults: List<SymbolMatch> = emptyList(),
    val pickerSearching: Boolean = false
)

class AlertsViewModel(
    private val alertDao: AlertDao,
    private val eventDao: AlertEventDao,
    private val priceCacheDao: PriceCacheDao,
    private val marketDataRepository: MarketDataRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(AlertsUiState())
    val ui: StateFlow<AlertsUiState> = _ui.asStateFlow()

    /** All alerts, sorted by symbol then id, observed live. */
    val alerts: StateFlow<List<AlertEntity>> =
        alertDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var pickerJob: Job? = null

    init {
        viewModelScope.launch {
            alertDao.observeAll()
                .combine(eventDao.observeRecent()) { alertList, events ->
                    val symbols = alertList.map { it.symbol }.distinct()
                    val priceMap = symbols.associateWith {
                        priceCacheDao.getOne(it)?.price
                    }
                    val rows = alertList.map { a ->
                        val live = priceMap[a.symbol] ?: a.lastPrice
                        AlertRow(
                            entity = a,
                            livePrice = live,
                            distancePct = computeDistancePct(a, live)
                        )
                    }
                    rows to events
                }
                .collect { (rows, events) ->
                    _ui.update { it.copy(rows = rows, events = events) }
                }
        }
    }

    private fun computeDistancePct(a: AlertEntity, livePrice: Double?): Double? {
        if (livePrice == null || livePrice <= 0.0) return null
        return when (a.type) {
            AlertType.PRICE_ABOVE -> ((a.threshold - livePrice) / livePrice) * 100.0
            AlertType.PRICE_BELOW -> ((a.threshold - livePrice) / livePrice) * 100.0
            else -> null // For percent / details-driven alerts, "distance" isn't meaningful.
        }
    }

    // ---- Toggle / Delete / Undo / Snooze ---------------------------------

    fun toggle(id: Long, enabled: Boolean) =
        viewModelScope.launch { alertDao.setEnabled(id, enabled) }

    fun confirmDelete(id: Long) = _ui.update { it.copy(confirmDeleteId = id) }
    fun cancelDelete() = _ui.update { it.copy(confirmDeleteId = null) }

    fun delete(id: Long) {
        viewModelScope.launch {
            val entity = alerts.value.firstOrNull { it.id == id }
            alertDao.delete(id)
            _ui.update { it.copy(confirmDeleteId = null, undoDeleteEntity = entity) }
        }
    }

    fun undoDelete() {
        val entity = _ui.value.undoDeleteEntity ?: return
        viewModelScope.launch {
            alertDao.insert(entity)
            _ui.update { it.copy(undoDeleteEntity = null) }
        }
    }

    fun dismissUndoSnackbar() = _ui.update { it.copy(undoDeleteEntity = null) }

    fun snooze(id: Long, durationMs: Long) {
        viewModelScope.launch {
            val entity = alerts.value.firstOrNull { it.id == id } ?: return@launch
            alertDao.update(
                entity.copy(snoozedUntilMillis = System.currentTimeMillis() + durationMs)
            )
        }
    }

    fun unsnooze(id: Long) {
        viewModelScope.launch {
            val entity = alerts.value.firstOrNull { it.id == id } ?: return@launch
            alertDao.update(entity.copy(snoozedUntilMillis = null))
        }
    }

    fun clearHistory() {
        viewModelScope.launch { eventDao.clearAll() }
    }

    // ---- Edit / Create dialog --------------------------------------------

    fun openCreate() = _ui.update {
        it.copy(
            dialogOpen = true,
            dialogEditing = null,
            dialogSymbol = "",
            dialogType = AlertType.PRICE_ABOVE,
            dialogThreshold = "",
            dialogNotes = "",
            dialogAutoDisable = false,
            dialogMarketHoursOnly = null,
            dialogCompanyName = null,
            dialogCurrentPrice = null,
            dialogPercentChange = null,
            dialogCurrency = null
        )
    }

    fun openEdit(id: Long) {
        val a = alerts.value.firstOrNull { it.id == id } ?: return
        _ui.update {
            it.copy(
                dialogOpen = true,
                dialogEditing = a,
                dialogSymbol = a.symbol,
                dialogType = a.type,
                dialogThreshold = formatThreshold(a),
                dialogNotes = a.notes.orEmpty(),
                dialogAutoDisable = a.autoDisableAfterFire,
                dialogMarketHoursOnly = a.marketHoursOnly,
                dialogCompanyName = null,
                dialogCurrentPrice = null,
                dialogPercentChange = null,
                dialogCurrency = null
            )
        }
        loadDialogQuote(a.symbol)
    }

    fun closeDialog() = _ui.update {
        it.copy(
            dialogOpen = false,
            dialogEditing = null,
            dialogCompanyName = null,
            dialogCurrentPrice = null,
            dialogPercentChange = null,
            dialogCurrency = null
        )
    }

    fun onDialogSymbolChange(v: String) =
        _ui.update { it.copy(dialogSymbol = v.trim().uppercase()) }

    fun onDialogTypeChange(t: AlertType) =
        _ui.update { it.copy(dialogType = t, dialogThreshold = defaultThresholdFor(t, it.dialogThreshold)) }

    fun onDialogThresholdChange(v: String) =
        _ui.update { it.copy(dialogThreshold = v) }

    fun onDialogNotesChange(v: String) =
        _ui.update { it.copy(dialogNotes = v) }

    fun onDialogAutoDisableChange(v: Boolean) =
        _ui.update { it.copy(dialogAutoDisable = v) }

    fun onDialogMarketHoursOnlyChange(v: Boolean?) =
        _ui.update { it.copy(dialogMarketHoursOnly = v) }

    fun saveDialog() {
        val s = _ui.value
        val symbol = s.dialogSymbol.trim().uppercase()
        if (symbol.isBlank()) return
        val threshold = parseThreshold(s.dialogType, s.dialogThreshold) ?: return
        val notes = s.dialogNotes.trim().ifBlank { null }
        viewModelScope.launch {
            val editing = s.dialogEditing
            if (editing == null) {
                alertDao.insert(
                    AlertEntity(
                        symbol = symbol,
                        type = s.dialogType,
                        threshold = threshold,
                        enabled = true,
                        notes = notes,
                        autoDisableAfterFire = s.dialogAutoDisable,
                        marketHoursOnly = s.dialogMarketHoursOnly
                    )
                )
            } else {
                alertDao.update(
                    editing.copy(
                        symbol = symbol,
                        type = s.dialogType,
                        threshold = threshold,
                        notes = notes,
                        autoDisableAfterFire = s.dialogAutoDisable,
                        marketHoursOnly = s.dialogMarketHoursOnly,
                        // Reset crossing state on threshold/type change so we
                        // don't keep an old "previously above" flag stuck.
                        lastCrossingState = null,
                        lastPercentTriggerDate = null
                    )
                )
            }
            _ui.update { it.copy(dialogOpen = false, dialogEditing = null) }
        }
    }

    // ---- Symbol picker (for FAB → create) --------------------------------

    fun openPicker() = _ui.update {
        it.copy(pickerOpen = true, pickerQuery = "", pickerResults = emptyList())
    }
    fun closePicker() = _ui.update { it.copy(pickerOpen = false) }

    fun onPickerQueryChange(q: String) {
        _ui.update { it.copy(pickerQuery = q) }
        pickerJob?.cancel()
        if (q.isBlank()) {
            _ui.update { it.copy(pickerResults = emptyList(), pickerSearching = false) }
            return
        }
        pickerJob = viewModelScope.launch {
            _ui.update { it.copy(pickerSearching = true) }
            delay(280)
            when (val r = marketDataRepository.search(q)) {
                is DataResult.Success -> _ui.update {
                    it.copy(pickerResults = r.value, pickerSearching = false)
                }
                is DataResult.Error -> _ui.update {
                    it.copy(pickerResults = emptyList(), pickerSearching = false)
                }
            }
        }
    }

    fun pickSymbol(symbol: String) {
        val normalized = symbol.trim().uppercase()
        val picked = _ui.value.pickerResults.firstOrNull {
            it.symbol.equals(normalized, ignoreCase = true)
        }
        _ui.update {
            it.copy(
                pickerOpen = false,
                dialogOpen = true,
                dialogEditing = null,
                dialogSymbol = normalized,
                dialogType = AlertType.PRICE_ABOVE,
                dialogThreshold = "",
                dialogNotes = "",
                dialogAutoDisable = false,
                dialogMarketHoursOnly = null,
                dialogCompanyName = picked?.name,
                dialogCurrentPrice = null,
                dialogPercentChange = null,
                dialogCurrency = null
            )
        }
        loadDialogQuote(normalized)
    }

    private fun loadDialogQuote(symbol: String) {
        if (symbol.isBlank()) return
        viewModelScope.launch {
            when (val result = marketDataRepository.getQuote(symbol, forceRefresh = false)) {
                is DataResult.Success -> applyDialogQuote(symbol, result.value)
                is DataResult.Error -> Unit
            }
        }
    }

    private fun applyDialogQuote(symbol: String, quote: Quote) {
        _ui.update {
            if (!it.dialogOpen || !it.dialogSymbol.equals(symbol, ignoreCase = true)) {
                it
            } else {
                it.copy(
                    dialogCompanyName = quote.name ?: it.dialogCompanyName,
                    dialogCurrentPrice = quote.price,
                    dialogPercentChange = quote.percentChange,
                    dialogCurrency = quote.currency
                )
            }
        }
    }

    private fun formatThreshold(a: AlertEntity): String = when (a.type) {
        AlertType.FIFTY_TWO_WEEK_HIGH,
        AlertType.FIFTY_TWO_WEEK_LOW,
        AlertType.MA200_CROSS_UP,
        AlertType.MA200_CROSS_DOWN,
        AlertType.ANALYST_TARGET_REACH -> ""
        else -> "%.2f".format(a.threshold)
    }

    private fun defaultThresholdFor(t: AlertType, current: String): String = when (t) {
        AlertType.EARNINGS_REMINDER -> if (current.isBlank()) "3" else current
        AlertType.VOLUME_SPIKE -> if (current.isBlank()) "2" else current
        AlertType.FIFTY_TWO_WEEK_HIGH,
        AlertType.FIFTY_TWO_WEEK_LOW,
        AlertType.MA200_CROSS_UP,
        AlertType.MA200_CROSS_DOWN,
        AlertType.ANALYST_TARGET_REACH -> ""
        else -> current
    }

    private fun parseThreshold(t: AlertType, raw: String): Double? = when (t) {
        AlertType.FIFTY_TWO_WEEK_HIGH,
        AlertType.FIFTY_TWO_WEEK_LOW,
        AlertType.MA200_CROSS_UP,
        AlertType.MA200_CROSS_DOWN,
        AlertType.ANALYST_TARGET_REACH -> 0.0 // unused but keep schema simple
        else -> raw.replace(",", ".").toDoubleOrNull()?.takeIf { it > 0 }
    }
}
