package com.stockwatchdog.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watchlist")
data class WatchlistItemEntity(
    @PrimaryKey val symbol: String,
    val name: String? = null,
    val position: Int = 0,
    val addedAtMillis: Long = System.currentTimeMillis(),
    /** Your manual average entry price per share. Null when not tracked. */
    val entryPrice: Double? = null,
    /** Your quantity (supports fractional). Null when not tracked. */
    val quantity: Double? = null,
    /** Optional free-text note, e.g. broker account, thesis, strategy tag. */
    val notes: String? = null
)
