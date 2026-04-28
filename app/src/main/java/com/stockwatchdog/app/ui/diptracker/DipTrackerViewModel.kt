package com.stockwatchdog.app.ui.diptracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stockwatchdog.app.data.api.MarketDataRepository
import com.stockwatchdog.app.data.db.DipTrackerDao
import com.stockwatchdog.app.data.db.entities.DipTrackerEntity
import com.stockwatchdog.app.domain.DataResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ZoneStatus(val label: String) {
    ABOVE_ZONE("Above zone"),
    NEAR_ZONE("Near zone"),
    IN_BUY_ZONE("In buy zone"),
    BELOW_ZONE("Below zone"),
    STRONG_BUY("Strong buy"),
    NO_DATA("--")
}

data class DipRow(
    val entity: DipTrackerEntity,
    val currentPrice: Double? = null,
    val name: String? = null,
    val status: ZoneStatus = ZoneStatus.NO_DATA,
    val distanceToBuyZonePct: Double? = null
)

data class DipTrackerUiState(
    val rows: List<DipRow> = emptyList(),
    val isRefreshing: Boolean = false,
    val dialogOpen: Boolean = false,
    val dialogEditingId: Long? = null,
    val symbolDraft: String = "",
    val buyZoneLowDraft: String = "",
    val buyZoneHighDraft: String = "",
    val strongBuyDraft: String = "",
    val notesDraft: String = "",
    val confirmDeleteId: Long? = null,
    val undoDeleteEntity: DipTrackerEntity? = null
)

class DipTrackerViewModel(
    private val dao: DipTrackerDao,
    private val repo: MarketDataRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(DipTrackerUiState())
    val ui: StateFlow<DipTrackerUiState> = _ui.asStateFlow()

    private var latestEntities: List<DipTrackerEntity> = emptyList()

    init {
        viewModelScope.launch {
            dao.observeAll().collectLatest { entities ->
                latestEntities = entities
                loadRows(entities, forceRefresh = false)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { loadRows(latestEntities, forceRefresh = true) }
    }

    private suspend fun loadRows(
        entities: List<DipTrackerEntity>,
        forceRefresh: Boolean
    ) {
        _ui.update { it.copy(isRefreshing = true) }
        val rows = entities.map { entity ->
            val result = repo.getQuote(entity.symbol, forceRefresh = forceRefresh)
            val price = (result as? DataResult.Success)?.value?.price
            val name = (result as? DataResult.Success)?.value?.name
            val status = computeStatus(price, entity)
            val distPct = computeDistancePct(price, entity)
            DipRow(
                entity = entity,
                currentPrice = price,
                name = name,
                status = status,
                distanceToBuyZonePct = distPct
            )
        }.sortedWith(
            compareBy<DipRow> { statusRank(it.status) }
                .thenBy { it.distanceToBuyZonePct ?: Double.MAX_VALUE }
                .thenBy { it.entity.symbol }
        )
        _ui.update { it.copy(rows = rows, isRefreshing = false) }
    }

    private fun statusRank(status: ZoneStatus): Int = when (status) {
        ZoneStatus.STRONG_BUY -> 0
        ZoneStatus.IN_BUY_ZONE -> 1
        ZoneStatus.NEAR_ZONE -> 2
        ZoneStatus.BELOW_ZONE -> 3
        ZoneStatus.ABOVE_ZONE -> 4
        ZoneStatus.NO_DATA -> 5
    }

    private fun computeStatus(price: Double?, e: DipTrackerEntity): ZoneStatus {
        if (price == null) return ZoneStatus.NO_DATA
        val low = e.buyZoneLow
        val high = e.buyZoneHigh
        val strongBuy = e.strongBuyBelow
        return when {
            strongBuy != null && price <= strongBuy -> ZoneStatus.STRONG_BUY
            price < low -> ZoneStatus.BELOW_ZONE
            price in low..high -> ZoneStatus.IN_BUY_ZONE
            price <= high * 1.03 -> ZoneStatus.NEAR_ZONE
            else -> ZoneStatus.ABOVE_ZONE
        }
    }

    private fun computeDistancePct(price: Double?, e: DipTrackerEntity): Double? {
        if (price == null) return null
        return when {
            price > e.buyZoneHigh && e.buyZoneHigh > 0 ->
                (price - e.buyZoneHigh) / e.buyZoneHigh * 100.0
            price < e.buyZoneLow && e.buyZoneLow > 0 ->
                (e.buyZoneLow - price) / e.buyZoneLow * 100.0
            else -> 0.0
        }
    }

    fun openAddDialog() = _ui.update {
        it.copy(
            dialogOpen = true,
            dialogEditingId = null,
            symbolDraft = "",
            buyZoneLowDraft = "",
            buyZoneHighDraft = "",
            strongBuyDraft = "",
            notesDraft = ""
        )
    }

    fun openEditDialog(id: Long) {
        val entity = latestEntities.firstOrNull { it.id == id } ?: return
        _ui.update {
            it.copy(
                dialogOpen = true,
                dialogEditingId = id,
                symbolDraft = entity.symbol,
                buyZoneLowDraft = trimTrailingZeros(entity.buyZoneLow),
                buyZoneHighDraft = trimTrailingZeros(entity.buyZoneHigh),
                strongBuyDraft = entity.strongBuyBelow?.let(::trimTrailingZeros) ?: "",
                notesDraft = entity.notes ?: ""
            )
        }
    }

    fun closeDialog() = _ui.update {
        it.copy(
            dialogOpen = false,
            dialogEditingId = null,
            symbolDraft = "",
            buyZoneLowDraft = "",
            buyZoneHighDraft = "",
            strongBuyDraft = "",
            notesDraft = ""
        )
    }

    fun onSymbolChange(v: String) = _ui.update { it.copy(symbolDraft = v) }
    fun onBuyZoneLowChange(v: String) = _ui.update { it.copy(buyZoneLowDraft = v) }
    fun onBuyZoneHighChange(v: String) = _ui.update { it.copy(buyZoneHighDraft = v) }
    fun onStrongBuyChange(v: String) = _ui.update { it.copy(strongBuyDraft = v) }
    fun onNotesChange(v: String) = _ui.update { it.copy(notesDraft = v) }

    fun save() {
        val s = _ui.value
        val symbol = s.symbolDraft.trim().uppercase()
        val low = s.buyZoneLowDraft.toDoubleOrNull() ?: return
        val high = s.buyZoneHighDraft.toDoubleOrNull() ?: return
        if (symbol.isBlank() || low <= 0 || high <= 0 || low > high) return
        val strongBuy = s.strongBuyDraft.toDoubleOrNull()
        if (strongBuy != null && strongBuy > low) return
        val notes = s.notesDraft.trim().ifBlank { null }

        viewModelScope.launch {
            val editId = s.dialogEditingId
            if (editId == null) {
                dao.insert(
                    DipTrackerEntity(
                        symbol = symbol,
                        buyZoneLow = low,
                        buyZoneHigh = high,
                        strongBuyBelow = strongBuy,
                        notes = notes
                    )
                )
            } else {
                val existing = latestEntities.firstOrNull { it.id == editId }
                if (existing != null) {
                    dao.update(
                        existing.copy(
                            symbol = symbol,
                            buyZoneLow = low,
                            buyZoneHigh = high,
                            strongBuyBelow = strongBuy,
                            notes = notes
                        )
                    )
                }
            }
            closeDialog()
        }
    }

    fun confirmDelete(id: Long) = _ui.update { it.copy(confirmDeleteId = id) }
    fun cancelDelete() = _ui.update { it.copy(confirmDeleteId = null) }

    fun delete(id: Long) {
        viewModelScope.launch {
            val entity = latestEntities.firstOrNull { it.id == id }
            dao.deleteById(id)
            _ui.update { it.copy(confirmDeleteId = null, undoDeleteEntity = entity) }
        }
    }

    fun undoDelete() {
        val entity = _ui.value.undoDeleteEntity ?: return
        viewModelScope.launch {
            dao.insert(entity)
            _ui.update { it.copy(undoDeleteEntity = null) }
        }
    }

    fun dismissUndoSnackbar() = _ui.update { it.copy(undoDeleteEntity = null) }

    private fun trimTrailingZeros(d: Double): String {
        val s = "%.4f".format(d)
        return s.trimEnd('0').trimEnd('.')
    }
}
