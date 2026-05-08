package com.stockwatchdog.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached Dip Finder analysis for a single ticker. Stored in Room so the
 * UI can render instantly on cold start and so we only re-run the
 * (expensive) free-API fetch every [DipFinderResultEntity.DEFAULT_TTL_MS].
 *
 * One row per ticker (symbol is the primary key — case-normalised
 * upstream). Re-running an analysis on the same ticker upserts.
 */
@Entity(tableName = "dip_finder_results")
data class DipFinderResultEntity(
    @PrimaryKey val symbol: String,
    val name: String?,
    val currentPrice: Double?,
    val high52w: Double?,
    val low52w: Double?,
    val ma200: Double?,
    val pctFromHigh: Double?,
    val pctFromLow: Double?,
    val nearLow: Boolean,
    val revenueGrowthYoYPct: Double?,
    val profitMarginPct: Double?,
    val debtToEquity: Double?,
    val epsTtm: Double?,
    val score: Int,
    /** Stored as the enum name so we can rename display labels safely later. */
    val label: String,
    /** Stored as the enum name. */
    val confidence: String,
    val reason: String,
    val computedAtMillis: Long
) {
    companion object {
        /** How long a cached analysis is considered fresh. */
        const val DEFAULT_TTL_MS: Long = 24L * 60L * 60L * 1000L
    }
}
