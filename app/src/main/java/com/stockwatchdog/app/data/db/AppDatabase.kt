package com.stockwatchdog.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.stockwatchdog.app.data.db.entities.AlertEntity
import com.stockwatchdog.app.data.db.entities.AlertEventEntity
import com.stockwatchdog.app.data.db.entities.Converters
import com.stockwatchdog.app.data.db.entities.DipFinderResultEntity
import com.stockwatchdog.app.data.db.entities.DipFinderWatchlistEntity
import com.stockwatchdog.app.data.db.entities.DipTrackerEntity
import com.stockwatchdog.app.data.db.entities.PositionLotEntity
import com.stockwatchdog.app.data.db.entities.PriceCacheEntity
import com.stockwatchdog.app.data.db.entities.WatchlistItemEntity

@Database(
    entities = [
        WatchlistItemEntity::class,
        AlertEntity::class,
        AlertEventEntity::class,
        PriceCacheEntity::class,
        PositionLotEntity::class,
        DipTrackerEntity::class,
        DipFinderResultEntity::class,
        DipFinderWatchlistEntity::class
    ],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun watchlistDao(): WatchlistDao
    abstract fun alertDao(): AlertDao
    abstract fun alertEventDao(): AlertEventDao
    abstract fun priceCacheDao(): PriceCacheDao
    abstract fun positionLotDao(): PositionLotDao
    abstract fun dipTrackerDao(): DipTrackerDao
    abstract fun dipFinderDao(): DipFinderDao

    companion object {
        /** v1 → v2: add manual entry-position fields to the watchlist table. */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE watchlist ADD COLUMN entryPrice REAL")
                db.execSQL("ALTER TABLE watchlist ADD COLUMN quantity REAL")
                db.execSQL("ALTER TABLE watchlist ADD COLUMN notes TEXT")
            }
        }

        /**
         * v2 → v3: introduce the `position_lots` table so users can record
         * multiple entry points per ticker. Existing single-entry rows in
         * `watchlist` are seeded as a single "Position 1" lot. The legacy
         * entryPrice/quantity columns stay on the watchlist table (read-only,
         * unused by new code) so old clients and alerts keep working without
         * a destructive rewrite.
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS position_lots (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        symbol TEXT NOT NULL,
                        entryPrice REAL NOT NULL,
                        amountInvested REAL NOT NULL,
                        addedAtMillis INTEGER NOT NULL,
                        FOREIGN KEY(symbol) REFERENCES watchlist(symbol)
                            ON UPDATE CASCADE ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_position_lots_symbol " +
                        "ON position_lots(symbol)"
                )
                // Seed lots from any existing manual entry prices. Users who
                // never set a quantity get amountInvested = entryPrice so the
                // percentage P/L continues to work; the dollar total will be
                // small but accurate for what they originally entered.
                db.execSQL(
                    """
                    INSERT INTO position_lots
                        (symbol, entryPrice, amountInvested, addedAtMillis)
                    SELECT symbol,
                           entryPrice,
                           entryPrice * COALESCE(quantity, 1.0),
                           addedAtMillis
                    FROM watchlist
                    WHERE entryPrice IS NOT NULL AND entryPrice > 0
                    """.trimIndent()
                )
            }
        }

        /** v3 → v4: add optional `platform` column to position_lots. */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE position_lots ADD COLUMN platform TEXT")
            }
        }

        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS dip_tracker (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        symbol TEXT NOT NULL,
                        buyZoneLow REAL NOT NULL,
                        buyZoneHigh REAL NOT NULL,
                        strongBuyBelow REAL,
                        notes TEXT,
                        addedAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_dip_tracker_symbol ON dip_tracker(symbol)"
                )
            }
        }

        /**
         * v5 → v6: introduce the Dip Finder feature. Adds two tables:
         *  - `dip_finder_results`  cached analysis (one row per ticker)
         *  - `dip_finder_watchlist` user-tracked tickers for the finder
         *
         * Both are additive — nothing in earlier tables is touched.
         */
        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS dip_finder_results (
                        symbol TEXT NOT NULL PRIMARY KEY,
                        name TEXT,
                        currentPrice REAL,
                        high52w REAL,
                        low52w REAL,
                        ma200 REAL,
                        pctFromHigh REAL,
                        pctFromLow REAL,
                        nearLow INTEGER NOT NULL,
                        revenueGrowthYoYPct REAL,
                        profitMarginPct REAL,
                        debtToEquity REAL,
                        epsTtm REAL,
                        score INTEGER NOT NULL,
                        label TEXT NOT NULL,
                        confidence TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        computedAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS dip_finder_watchlist (
                        symbol TEXT NOT NULL PRIMARY KEY,
                        addedAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * v6 → v7: Alerts upgrade.
         *  - New `alert_events` table records every fired alert so the
         *    History tab can show what/when/why even after the system
         *    notification has been dismissed.
         *  - New columns on `alerts`: snooze, auto-disable-after-fire,
         *    optional notes, per-alert market-hours-only override, and
         *    last-known price (used by the UI to render distance-to-trigger).
         */
        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS alert_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        alertId INTEGER,
                        symbol TEXT NOT NULL,
                        type TEXT NOT NULL,
                        message TEXT NOT NULL,
                        priceAtTrigger REAL,
                        threshold REAL,
                        firedAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_alert_events_symbol ON alert_events(symbol)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_alert_events_firedAtMillis ON alert_events(firedAtMillis)"
                )
                db.execSQL("ALTER TABLE alerts ADD COLUMN autoDisableAfterFire INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE alerts ADD COLUMN snoozedUntilMillis INTEGER")
                db.execSQL("ALTER TABLE alerts ADD COLUMN notes TEXT")
                db.execSQL("ALTER TABLE alerts ADD COLUMN marketHoursOnly INTEGER")
                db.execSQL("ALTER TABLE alerts ADD COLUMN lastPrice REAL")
            }
        }
    }
}
