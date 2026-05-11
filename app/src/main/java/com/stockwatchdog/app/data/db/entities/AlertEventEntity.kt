package com.stockwatchdog.app.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persistent log of every alert that has fired. Powers the History tab on
 * the Alerts screen so the user can see *what triggered, when, at what
 * price* even if they missed the system notification.
 *
 * `alertId` is intentionally nullable: if the user later deletes the
 * underlying alert we still want to keep the historical record. `type` is
 * stored as the enum **name** so renaming an enum value won't break old
 * rows.
 */
@Entity(
    tableName = "alert_events",
    indices = [Index("symbol"), Index("firedAtMillis")]
)
data class AlertEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alertId: Long? = null,
    val symbol: String,
    /** Stored as enum name so deletes/renames don't crash the read. */
    val type: String,
    val message: String,
    val priceAtTrigger: Double? = null,
    val threshold: Double? = null,
    val firedAtMillis: Long
)
