package com.stockwatchdog.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.stockwatchdog.app.data.db.entities.PriceCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PriceCacheDao {
    @Query("SELECT * FROM price_cache WHERE symbol IN (:symbols)")
    fun observe(symbols: List<String>): Flow<List<PriceCacheEntity>>

    @Query("SELECT * FROM price_cache WHERE symbol = :symbol")
    fun observeOne(symbol: String): Flow<PriceCacheEntity?>

    @Query("SELECT * FROM price_cache WHERE symbol = :symbol")
    suspend fun getOne(symbol: String): PriceCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PriceCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<PriceCacheEntity>)
}
