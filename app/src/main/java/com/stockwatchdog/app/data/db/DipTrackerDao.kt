package com.stockwatchdog.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.stockwatchdog.app.data.db.entities.DipTrackerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DipTrackerDao {
    @Query("SELECT * FROM dip_tracker ORDER BY addedAtMillis DESC, id DESC")
    fun observeAll(): Flow<List<DipTrackerEntity>>

    @Query("SELECT * FROM dip_tracker ORDER BY addedAtMillis DESC, id DESC")
    suspend fun getAll(): List<DipTrackerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: DipTrackerEntity): Long

    @Update
    suspend fun update(item: DipTrackerEntity)

    @Delete
    suspend fun delete(item: DipTrackerEntity)

    @Query("DELETE FROM dip_tracker WHERE id = :id")
    suspend fun deleteById(id: Long)
}
