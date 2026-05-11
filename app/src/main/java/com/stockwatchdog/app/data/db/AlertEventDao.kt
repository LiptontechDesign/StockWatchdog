package com.stockwatchdog.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.stockwatchdog.app.data.db.entities.AlertEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertEventDao {

    @Query("SELECT * FROM alert_events ORDER BY firedAtMillis DESC LIMIT 500")
    fun observeRecent(): Flow<List<AlertEventEntity>>

    @Query("SELECT COUNT(*) FROM alert_events WHERE firedAtMillis > :since")
    suspend fun countSince(since: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: AlertEventEntity): Long

    @Query("DELETE FROM alert_events WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM alert_events")
    suspend fun clearAll()

    /** Trim history to the most recent [keep] rows to stop unbounded growth. */
    @Query(
        "DELETE FROM alert_events WHERE id NOT IN " +
            "(SELECT id FROM alert_events ORDER BY firedAtMillis DESC LIMIT :keep)"
    )
    suspend fun trim(keep: Int)
}
