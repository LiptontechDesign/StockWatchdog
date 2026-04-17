package com.stockwatchdog.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun watchlistDao(): WatchlistDao
    abstract fun alertDao(): AlertDao
    abstract fun priceCacheDao(): PriceCacheDao

    companion object {
        /** v1 → v2: add manual entry-position fields to the watchlist table. */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE watchlist ADD COLUMN entryPrice REAL")
                db.execSQL("ALTER TABLE watchlist ADD COLUMN quantity REAL")
                db.execSQL("ALTER TABLE watchlist ADD COLUMN notes TEXT")
            }
        }
    }
}
