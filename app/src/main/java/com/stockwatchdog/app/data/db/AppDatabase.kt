package com.stockwatchdog.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.stockwatchdog.app.data.db.entities.AlertEntity
import com.stockwatchdog.app.data.db.entities.Converters
import com.stockwatchdog.app.data.db.entities.PriceCacheEntity
import com.stockwatchdog.app.data.db.entities.WatchlistItemEntity

@Database(
    entities = [
        WatchlistItemEntity::class,
        AlertEntity::class,
        PriceCacheEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun watchlistDao(): WatchlistDao
    abstract fun alertDao(): AlertDao
    abstract fun priceCacheDao(): PriceCacheDao
}
