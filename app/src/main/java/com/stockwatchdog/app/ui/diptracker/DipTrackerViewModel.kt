package com.stockwatchdog.app.ui.diptracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stockwatchdog.app.data.api.MarketDataRepository
import com.stockwatchdog.app.data.api.StockDetails
import com.stockwatchdog.app.data.api.StockDetailsRepository
import com.stockwatchdog.app.data.db.DipTrackerDao
import com.stockwatchdog.app.data.db.entities.DipTrackerEntity
import com.stockwatchdog.app.domain.DataResult
import com.stockwatchdog.app.domain.Quote
import com.stockwatchdog.app.domain.SymbolMatch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

enum class ZoneStatus(val label: String) {
    ABOVE_ZONE("Above zone"),
    NEAR_ZONE("Near zone"),
    IN_BUY_ZONE("In buy zone"),
    BELOW_ZONE("Below zone"),
    STRONG_BUY("Strong buy"),
    NO_DATA("--")
}

enum class DipSortMode(val label: String) {
    URGENCY("Urgency"),
    SYMBOL("Symbol"),
    DISTANCE("Distance"),
    PRICE("Price")
}

enum class DipFilterMode(val label: String) {
    ALL("All"),
    READY("Ready"),
    NEAR("Near"),
    WATCHING("Watching")
}

data class DipGroupFilterOption(
    val key: String,
    val label: String,
    val count: Int
) {
    companion object {
        const val ALL_KEY = "all"
        const val STOCKS_KEY = "asset:stock"
        const val ETFS_KEY = "asset:etf"
        private const val SECTOR_PREFIX = "sector:"

        fun all(count: Int) = DipGroupFilterOption(ALL_KEY, "All", count)
        fun stocks(count: Int) = DipGroupFilterOption(STOCKS_KEY, "Stocks", count)
        fun etfs(count: Int) = DipGroupFilterOption(ETFS_KEY, "ETFs", count)
        fun sector(label: String, count: Int) =
            DipGroupFilterOption(SECTOR_PREFIX + label.filterKeyPart(), label, count)

        fun sectorKey(label: String) = SECTOR_PREFIX + label.filterKeyPart()
    }
}

data class DipRow(
    val entity: DipTrackerEntity,
    val currentPrice: Double? = null,
    val previousClose: Double? = null,
    val percentChange: Double? = null,
    val name: String? = null,
    val status: ZoneStatus = ZoneStatus.NO_DATA,
    val distanceToBuyZonePct: Double? = null,
    val assetType: String? = null,
    val sector: String? = null,
    val industry: String? = null,
    /** Rich Yahoo quoteSummary details (earnings, target, 52w, MA200, volume). */
    val details: StockDetails? = null
)

data class DipTrackerUiState(
    val rows: List<DipRow> = emptyList(),
    val allRowsCount: Int = 0,
    val readyCount: Int = 0,
    val nearCount: Int = 0,
    val watchingCount: Int = 0,
    val isRefreshing: Boolean = false,
    val lastUpdatedAtMs: Long? = null,
    val sortMode: DipSortMode = DipSortMode.URGENCY,
    val filterMode: DipFilterMode = DipFilterMode.ALL,
    val groupFilters: List<DipGroupFilterOption> = listOf(DipGroupFilterOption.all(0)),
    val selectedGroupFilterKey: String = DipGroupFilterOption.ALL_KEY,
    val groupByStatus: Boolean = true,
    val dialogOpen: Boolean = false,
    val dialogEditingId: Long? = null,
    val symbolDraft: String = "",
    val buyZoneLowDraft: String = "",
    val buyZoneHighDraft: String = "",
    val strongBuyDraft: String = "",
    val notesDraft: String = "",
    val confirmDeleteId: Long? = null,
    val undoDeleteEntity: DipTrackerEntity? = null,
    val searchQuery: String = "",
    val searchResults: List<SymbolMatch> = emptyList(),
    val isSearching: Boolean = false,
    val selectedSymbol: SymbolMatch? = null,
    val selectedQuote: Quote? = null
)

class DipTrackerViewModel(
    private val dao: DipTrackerDao,
    private val repo: MarketDataRepository,
    private val detailsRepo: StockDetailsRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(DipTrackerUiState())
    val ui: StateFlow<DipTrackerUiState> = _ui.asStateFlow()

    private var latestEntities: List<DipTrackerEntity> = emptyList()
    private var rawRows: List<DipRow> = emptyList()
    private var searchJob: Job? = null

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
        val metadataUpdates = mutableListOf<DipTrackerEntity>()
        val rows = entities.map { entity ->
            val result = repo.getQuote(entity.symbol, forceRefresh = forceRefresh)
            val quote = (result as? DataResult.Success)?.value
            val price = quote?.price
            val name = quote?.name
            val status = computeStatus(price, entity)
            val distPct = computeDistancePct(price, entity)
            // Best-effort details fetch. The repo caches for 6h and degrades
            // gracefully if Yahoo's quoteSummary is rate-limited.
            val details = runCatching {
                detailsRepo.get(entity.symbol, forceRefresh = forceRefresh)
            }.getOrNull()
            val rowEntity = entity.withResolvedMetadata(details)
            if (rowEntity != entity) metadataUpdates += rowEntity
            DipRow(
                entity = rowEntity,
                currentPrice = price,
                previousClose = quote?.previousClose,
                percentChange = quote?.percentChange,
                name = name,
                status = status,
                distanceToBuyZonePct = distPct,
                assetType = rowEntity.assetType,
                sector = rowEntity.sector,
                industry = rowEntity.industry,
                details = details
            )
        }
        rawRows = rows
        val anyData = rows.any { it.status != ZoneStatus.NO_DATA }
        _ui.update { current ->
            val groupFilters = buildGroupFilters(rows)
            val selectedGroupKey = current.selectedGroupFilterKey
                .takeIf { key -> groupFilters.any { it.key == key } }
                ?: DipGroupFilterOption.ALL_KEY
            current.copy(
                rows = applyFilterSort(rows, current.sortMode, current.filterMode, selectedGroupKey),
                allRowsCount = rows.size,
                readyCount = rows.count { it.status == ZoneStatus.STRONG_BUY || it.status == ZoneStatus.IN_BUY_ZONE },
                nearCount = rows.count { it.status == ZoneStatus.NEAR_ZONE },
                watchingCount = rows.count {
                    it.status == ZoneStatus.ABOVE_ZONE || it.status == ZoneStatus.BELOW_ZONE || it.status == ZoneStatus.NO_DATA
                },
                groupFilters = groupFilters,
                selectedGroupFilterKey = selectedGroupKey,
                isRefreshing = false,
                lastUpdatedAtMs = if (anyData) System.currentTimeMillis() else current.lastUpdatedAtMs
            )
        }
        metadataUpdates.forEach { updated ->
            runCatching { dao.update(updated) }
        }
    }

    private fun applyFilterSort(
        rows: List<DipRow>,
        sort: DipSortMode,
        filter: DipFilterMode,
        groupFilterKey: String
    ): List<DipRow> {
        val statusFiltered = when (filter) {
            DipFilterMode.ALL -> rows
            DipFilterMode.READY -> rows.filter {
                it.status == ZoneStatus.STRONG_BUY || it.status == ZoneStatus.IN_BUY_ZONE
            }
            DipFilterMode.NEAR -> rows.filter { it.status == ZoneStatus.NEAR_ZONE }
            DipFilterMode.WATCHING -> rows.filter {
                it.status == ZoneStatus.ABOVE_ZONE ||
                    it.status == ZoneStatus.BELOW_ZONE ||
                    it.status == ZoneStatus.NO_DATA
            }
        }
        val filtered = applyGroupFilter(statusFiltered, groupFilterKey)
        return when (sort) {
            DipSortMode.URGENCY -> filtered.sortedWith(
                compareBy<DipRow> { statusRank(it.status) }
                    .thenBy { it.distanceToBuyZonePct ?: Double.MAX_VALUE }
                    .thenBy { it.entity.symbol }
            )
            DipSortMode.SYMBOL -> filtered.sortedBy { it.entity.symbol }
            DipSortMode.DISTANCE -> filtered.sortedBy { it.distanceToBuyZonePct ?: Double.MAX_VALUE }
            DipSortMode.PRICE -> filtered.sortedByDescending { it.currentPrice ?: -1.0 }
        }
    }

    fun onSortModeChange(mode: DipSortMode) {
        _ui.update { current ->
            current.copy(
                sortMode = mode,
                rows = applyFilterSort(rawRows, mode, current.filterMode, current.selectedGroupFilterKey)
            )
        }
    }

    fun onFilterModeChange(mode: DipFilterMode) {
        _ui.update { current ->
            current.copy(
                filterMode = mode,
                rows = applyFilterSort(rawRows, current.sortMode, mode, current.selectedGroupFilterKey)
            )
        }
    }

    fun onGroupFilterChange(key: String) {
        _ui.update { current ->
            val safeKey = key.takeIf { requested -> current.groupFilters.any { it.key == requested } }
                ?: DipGroupFilterOption.ALL_KEY
            current.copy(
                selectedGroupFilterKey = safeKey,
                rows = applyFilterSort(rawRows, current.sortMode, current.filterMode, safeKey)
            )
        }
    }

    fun toggleGroupByStatus() {
        _ui.update { it.copy(groupByStatus = !it.groupByStatus) }
    }

    private fun buildGroupFilters(rows: List<DipRow>): List<DipGroupFilterOption> {
        val stockCount = rows.count { it.assetType.isStockLike() }
        val etfCount = rows.count { it.assetType.isEtfLike() }
        val sectors = rows
            .filter { it.assetType.isStockLike() }
            .mapNotNull { it.sector.normalizeSector() }
            .groupingBy { it }
            .eachCount()

        return buildList {
            add(DipGroupFilterOption.all(rows.size))
            if (stockCount > 0) add(DipGroupFilterOption.stocks(stockCount))
            if (etfCount > 0) add(DipGroupFilterOption.etfs(etfCount))
            sectors.entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .forEach { (sector, count) -> add(DipGroupFilterOption.sector(sector, count)) }
        }
    }

    private fun applyGroupFilter(rows: List<DipRow>, key: String): List<DipRow> =
        when (key) {
            DipGroupFilterOption.ALL_KEY -> rows
            DipGroupFilterOption.STOCKS_KEY -> rows.filter { it.assetType.isStockLike() }
            DipGroupFilterOption.ETFS_KEY -> rows.filter { it.assetType.isEtfLike() }
            else -> rows.filter { row ->
                row.sector?.let { DipGroupFilterOption.sectorKey(it) } == key
            }
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
            notesDraft = "",
            searchQuery = "",
            searchResults = emptyList(),
            isSearching = false,
            selectedSymbol = null,
            selectedQuote = null
        )
    }

    fun openEditDialog(id: Long) {
        val entity = latestEntities.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            val quote = when (val res = repo.getQuote(entity.symbol)) {
                is DataResult.Success -> res.value
                is DataResult.Error -> null
            }
            _ui.update {
                it.copy(
                    dialogOpen = true,
                    dialogEditingId = id,
                    symbolDraft = entity.symbol,
                    buyZoneLowDraft = trimTrailingZeros(entity.buyZoneLow),
                    buyZoneHighDraft = trimTrailingZeros(entity.buyZoneHigh),
                    strongBuyDraft = entity.strongBuyBelow?.let(::trimTrailingZeros) ?: "",
                    notesDraft = entity.notes ?: "",
                    selectedSymbol = SymbolMatch(entity.symbol, quote?.name, null, entity.assetType),
                    selectedQuote = quote
                )
            }
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
            notesDraft = "",
            searchQuery = "",
            searchResults = emptyList(),
            isSearching = false,
            selectedSymbol = null,
            selectedQuote = null
        )
    }

    fun onSearchQueryChange(v: String) {
        _ui.update { it.copy(searchQuery = v) }
        searchJob?.cancel()
        if (v.length >= 2) {
            searchJob = viewModelScope.launch {
                delay(300)
                _ui.update { it.copy(isSearching = true) }
                when (val res = repo.search(v)) {
                    is DataResult.Success -> _ui.update { it.copy(searchResults = res.value, isSearching = false) }
                    is DataResult.Error -> _ui.update { it.copy(searchResults = emptyList(), isSearching = false) }
                }
            }
        } else {
            _ui.update { it.copy(searchResults = emptyList(), isSearching = false) }
        }
    }

    fun selectSymbol(match: SymbolMatch) {
        searchJob?.cancel()
        viewModelScope.launch {
            _ui.update { it.copy(isSearching = true, searchQuery = "", searchResults = emptyList()) }
            val quote = when (val res = repo.getQuote(match.symbol)) {
                is DataResult.Success -> res.value
                is DataResult.Error -> null
            }
            _ui.update {
                it.copy(
                    selectedSymbol = match,
                    selectedQuote = quote,
                    symbolDraft = match.symbol,
                    isSearching = false
                )
            }
        }
    }

    fun clearSelectedSymbol() {
        _ui.update {
            it.copy(
                selectedSymbol = null,
                selectedQuote = null,
                symbolDraft = "",
                buyZoneLowDraft = "",
                buyZoneHighDraft = "",
                strongBuyDraft = ""
            )
        }
    }

    fun applyPresetBuyZone(percentBelow: Double) {
        val price = _ui.value.selectedQuote?.price ?: return
        val high = price * (1 - percentBelow / 100)
        val low = high * 0.98
        _ui.update {
            it.copy(
                buyZoneHighDraft = trimTrailingZeros(high),
                buyZoneLowDraft = trimTrailingZeros(low)
            )
        }
    }

    fun applyPresetStrongBuy(percentBelowLow: Double) {
        val low = _ui.value.buyZoneLowDraft.toDoubleOrNull() ?: return
        val strongBuy = low * (1 - percentBelowLow / 100)
        _ui.update { it.copy(strongBuyDraft = trimTrailingZeros(strongBuy)) }
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
        val selectedAssetType = s.selectedSymbol?.type.normalizeAssetType()

        viewModelScope.launch {
            val editId = s.dialogEditingId
            if (editId == null) {
                dao.insert(
                    DipTrackerEntity(
                        symbol = symbol,
                        buyZoneLow = low,
                        buyZoneHigh = high,
                        strongBuyBelow = strongBuy,
                        notes = notes,
                        assetType = selectedAssetType,
                        metadataUpdatedAtMillis = selectedAssetType?.let { System.currentTimeMillis() }
                    )
                )
            } else {
                val existing = latestEntities.firstOrNull { it.id == editId }
                if (existing != null) {
                    val sameSymbol = existing.symbol.equals(symbol, ignoreCase = true)
                    dao.update(
                        existing.copy(
                            symbol = symbol,
                            buyZoneLow = low,
                            buyZoneHigh = high,
                            strongBuyBelow = strongBuy,
                            notes = notes,
                            assetType = selectedAssetType ?: existing.assetType.takeIf { sameSymbol },
                            sector = existing.sector.takeIf { sameSymbol },
                            industry = existing.industry.takeIf { sameSymbol },
                            metadataUpdatedAtMillis = if (sameSymbol) {
                                existing.metadataUpdatedAtMillis
                            } else {
                                selectedAssetType?.let { System.currentTimeMillis() }
                            }
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
        val s = String.format(Locale.US, "%.2f", d)
        return s.trimEnd('0').trimEnd('.')
    }
}

private fun DipTrackerEntity.withResolvedMetadata(details: StockDetails?): DipTrackerEntity {
    val resolvedSector = details?.sector.normalizeSector() ?: sector.normalizeSector()
    val resolvedIndustry = details?.industry.normalizeMetadataText() ?: industry.normalizeMetadataText()
    val resolvedAssetType = details?.assetType.normalizeAssetType()
        ?: assetType.normalizeAssetType()
        ?: resolvedSector.assetTypeFromSector()

    if (
        assetType == resolvedAssetType &&
        sector == resolvedSector &&
        industry == resolvedIndustry
    ) {
        return this
    }

    return copy(
        assetType = resolvedAssetType,
        sector = resolvedSector,
        industry = resolvedIndustry,
        metadataUpdatedAtMillis = System.currentTimeMillis()
    )
}

private fun String?.normalizeAssetType(): String? {
    val text = normalizeMetadataText() ?: return null
    val lower = text.lowercase(Locale.US)
    return when {
        "etf" in lower || "exchange traded" in lower -> "ETF"
        "stock" in lower || "equity" in lower || "common" in lower -> "Stock"
        "index" in lower -> "Index"
        "fund" in lower -> "Fund"
        else -> text
    }
}

private fun String?.normalizeSector(): String? =
    normalizeMetadataText()?.split(" ", "-", "/")?.joinToString(" ") { word ->
        if (word.length <= 3 && word.all { it.isUpperCase() }) {
            word
        } else {
            word.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) }
        }
    }

private fun String?.normalizeMetadataText(): String? =
    this
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.takeUnless { it.equals("none", ignoreCase = true) }
        ?.takeUnless { it.equals("null", ignoreCase = true) }
        ?.takeUnless { it.equals("n/a", ignoreCase = true) }
        ?.takeUnless { it == "--" || it == "-" }

private fun String?.assetTypeFromSector(): String? =
    when {
        normalizeMetadataText()?.contains("ETF", ignoreCase = true) == true -> "ETF"
        normalizeMetadataText() != null -> "Stock"
        else -> null
    }

private fun String?.isEtfLike(): Boolean =
    normalizeAssetType()?.equals("ETF", ignoreCase = true) == true

private fun String?.isStockLike(): Boolean =
    normalizeAssetType()?.equals("Stock", ignoreCase = true) == true

private fun String.filterKeyPart(): String =
    trim()
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
