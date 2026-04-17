package com.stockwatchdog.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.stockwatchdog.app.data.db.entities.AlertEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {
    @Query("SELECT * FROM alerts ORDER BY symbol ASC, id ASC")
    fun observeAll(): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alerts WHERE enabled = 1")
    suspend fun getAllEnabled(): List<AlertEntity>

    @Query("SELECT * FROM alerts WHERE symbol = :symbol ORDER BY id ASC")
    fun observeBySymbol(symbol: String): Flow<List<AlertEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alert: AlertEntity): Long

    @Update
    suspend fun update(alert: AlertEntity)

    @Query("DELETE FROM alerts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE alerts SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)
}
