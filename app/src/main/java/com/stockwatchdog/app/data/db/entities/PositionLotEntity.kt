package com.stockwatchdog.app.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single buy at a single entry price. A symbol can have multiple lots
 * ("Position 1", "Position 2", ...) so the user can record buying more over
 * time without losing the original cost basis.
 *
 * The user enters [entryPrice] and [amountInvested] (dollars put in at that
 * entry). Share count is derived as amountInvested / entryPrice; we never
 * show it in the UI but it is implied by these two numbers.
 */
@Entity(
    tableName = "position_lots",
    foreignKeys = [
        ForeignKey(
            entity = WatchlistItemEntity::class,
            parentColumns = ["symbol"],
            childColumns = ["symbol"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [Index("symbol")]
)
data class PositionLotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    val entryPrice: Double,
    val amountInvested: Double,
    val addedAtMillis: Long = System.currentTimeMillis(),
    /** Optional broker/platform name (e.g. "Ndovu", "Hisa"). */
    val platform: String? = null
)
