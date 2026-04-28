package com.stockwatchdog.app.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dip_tracker",
    indices = [Index("symbol")]
)
data class DipTrackerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    val buyZoneLow: Double,
    val buyZoneHigh: Double,
    val strongBuyBelow: Double? = null,
    val notes: String? = null,
    val addedAtMillis: Long = System.currentTimeMillis()
)
