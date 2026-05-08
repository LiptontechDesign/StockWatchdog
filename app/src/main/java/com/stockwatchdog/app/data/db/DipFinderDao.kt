package com.stockwatchdog.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.stockwatchdog.app.data.db.entities.DipFinderResultEntity
import com.stockwatchdog.app.data.db.entities.DipFinderWatchlistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DipFinderDao {

    // ---- Cached analysis results --------------------------------------

    @Query("SELECT * FROM dip_finder_results")
    fun observeResults(): Flow<List<DipFinderResultEntity>>

    @Query("SELECT * FROM dip_finder_results WHERE symbol IN (:symbols)")
    suspend fun getResults(symbols: List<String>): List<DipFinderResultEntity>

    @Query("SELECT * FROM dip_finder_results WHERE symbol = :symbol LIMIT 1")
    suspend fun getResult(symbol: String): DipFinderResultEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertResult(result: DipFinderResultEntity)

    @Query("DELETE FROM dip_finder_results WHERE symbol = :symbol")
    suspend fun deleteResult(symbol: String)

    // ---- User watchlist -----------------------------------------------

    @Query("SELECT * FROM dip_finder_watchlist ORDER BY addedAtMillis ASC")
    fun observeWatchlist(): Flow<List<DipFinderWatchlistEntity>>

    @Query("SELECT symbol FROM dip_finder_watchlist")
    suspend fun watchlistSymbols(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addToWatchlist(item: DipFinderWatchlistEntity)

    @Query("DELETE FROM dip_finder_watchlist WHERE symbol = :symbol")
    suspend fun removeFromWatchlist(symbol: String)
}
