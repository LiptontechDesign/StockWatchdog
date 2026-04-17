package com.stockwatchdog.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watchlist")
data class WatchlistItemEntity(
    @PrimaryKey val symbol: String,
    val name: String? = null,
    val position: Int = 0,
    val addedAtMillis: Long = System.currentTimeMillis()
)
