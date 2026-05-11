package com.finunity.data.local.migration

import androidx.room.migration.Migration

/**
 * Database migration from version 3 to 4.
 */
object Migration3To4 {
    val migration: Migration = object : Migration(3, 4) {
        override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
            // No schema changes needed for v3->v4
        }
    }
}

/**
 * Database migration from version 4 to 5.
 * Adds AssetRecord table for new multi-asset model.
 */
object Migration4To5 {
    val migration: Migration = object : Migration(4, 5) {
        override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
            // Create new asset_records table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS asset_records (
                    id TEXT PRIMARY KEY,
                    accountId TEXT NOT NULL,
                    assetType TEXT NOT NULL,
                    riskBucket TEXT NOT NULL,
                    name TEXT NOT NULL,
                    quantity REAL NOT NULL,
                    cost REAL NOT NULL,
                    currentPrice REAL NOT NULL,
                    currency TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    FOREIGN KEY (accountId) REFERENCES accounts(id) ON DELETE CASCADE
                )
            """.trimIndent())

            // Create index for faster queries
            database.execSQL("CREATE INDEX IF NOT EXISTS index_asset_records_accountId ON asset_records(accountId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_asset_records_assetType ON asset_records(assetType)")
        }
    }
}

/**
 * Database migration from version 5 to 6.
 * Adds PriceHistory table for tracking price/cost changes over time.
 */
object Migration5To6 {
    val migration: Migration = object : Migration(5, 6) {
        override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
            // Create price_history table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS price_history (
                    id TEXT PRIMARY KEY,
                    recordId TEXT NOT NULL,
                    price REAL NOT NULL,
                    cost REAL NOT NULL,
                    timestamp INTEGER NOT NULL,
                    FOREIGN KEY (recordId) REFERENCES asset_records(id) ON DELETE CASCADE
                )
            """.trimIndent())

            // Create indexes for faster queries
            database.execSQL("CREATE INDEX IF NOT EXISTS index_price_history_recordId ON price_history(recordId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_price_history_timestamp ON price_history(timestamp)")
        }
    }
}

/**
 * Database migration from version 6 to 7.
 * Adds optional recordId column to transactions table for precise asset tracking.
 */
object Migration6To7 {
    val migration: Migration = object : Migration(6, 7) {
        override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE transactions ADD COLUMN recordId TEXT")
            // Add index for recordId queries
            database.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_recordId ON transactions(recordId)")
        }
    }
}

/**
 * Provider for all database migrations.
 */
object DatabaseMigrations {
    val ALL_MIGRATIONS: List<Migration> = listOf(
        Migration3To4.migration,
        Migration4To5.migration,
        Migration5To6.migration,
        Migration6To7.migration
    )
}