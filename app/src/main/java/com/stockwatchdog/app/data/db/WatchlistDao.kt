package com.stockwatchdog.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.stockwatchdog.app.data.db.entities.WatchlistItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchlistDao {
    @Query("SELECT * FROM watchlist ORDER BY position ASC, addedAtMillis ASC")
    fun observeAll(): Flow<List<WatchlistItemEntity>>

    @Query("SELECT * FROM watchlist ORDER BY position ASC, addedAtMillis ASC")
    suspend fun getAll(): List<WatchlistItemEntity>

    @Query("SELECT * FROM watchlist WHERE symbol = :symbol LIMIT 1")
    suspend fun getBySymbol(symbol: String): WatchlistItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: WatchlistItemEntity)

    @Query("DELETE FROM watchlist WHERE symbol = :symbol")
    suspend fun deleteBySymbol(symbol: String)

    @Query("UPDATE watchlist SET position = :position WHERE symbol = :symbol")
    suspend fun updatePosition(symbol: String, position: Int)

    @Transaction
    suspend fun reorder(symbolsInOrder: List<String>) {
        symbolsInOrder.forEachIndexed { index, sym -> updatePosition(sym, index) }
    }

    @Query("SELECT COUNT(*) FROM watchlist")
    suspend fun count(): Int
}
