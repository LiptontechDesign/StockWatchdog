package com.stockwatchdog.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A ticker the user has explicitly added to the Dip Finder watchlist.
 * Kept in its own table so it stays separate from the existing manual
 * "buy zone" tracker (`dip_tracker`) and from the generic [WatchlistItemEntity].
 *
 * The actual analysis lives in [DipFinderResultEntity]; this table is just
 * the user's saved list of symbols to keep watching.
 */
@Entity(tableName = "dip_finder_watchlist")
data class DipFinderWatchlistEntity(
    @PrimaryKey val symbol: String,
    val addedAtMillis: Long = System.currentTimeMillis()
)
