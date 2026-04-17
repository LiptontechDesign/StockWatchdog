package com.stockwatchdog.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "price_cache")
data class PriceCacheEntity(
    @PrimaryKey val symbol: String,
    val price: Double,
    val previousClose: Double?,
    val change: Double?,
    val percentChange: Double?,
    val open: Double?,
    val high: Double?,
    val low: Double?,
    val volume: Long?,
    val marketIsOpen: Boolean?,
    val currency: String?,
    val name: String?,
    val fetchedAtMillis: Long
)
