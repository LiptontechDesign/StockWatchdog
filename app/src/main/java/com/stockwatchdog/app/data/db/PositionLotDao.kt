package com.stockwatchdog.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.stockwatchdog.app.data.db.entities.PositionLotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PositionLotDao {

    @Query("SELECT * FROM position_lots WHERE symbol = :symbol ORDER BY addedAtMillis ASC, id ASC")
    fun observeBySymbol(symbol: String): Flow<List<PositionLotEntity>>

    @Query("SELECT * FROM position_lots WHERE symbol = :symbol ORDER BY addedAtMillis ASC, id ASC")
    suspend fun getBySymbol(symbol: String): List<PositionLotEntity>

    @Query("SELECT * FROM position_lots ORDER BY symbol ASC, addedAtMillis ASC, id ASC")
    fun observeAll(): Flow<List<PositionLotEntity>>

    @Query("SELECT * FROM position_lots ORDER BY symbol ASC, addedAtMillis ASC, id ASC")
    suspend fun getAll(): List<PositionLotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(lot: PositionLotEntity): Long

    @Update
    suspend fun update(lot: PositionLotEntity)

    @Delete
    suspend fun delete(lot: PositionLotEntity)

    @Query("DELETE FROM position_lots WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM position_lots WHERE symbol = :symbol")
    suspend fun deleteBySymbol(symbol: String)
}
