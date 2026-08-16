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
 * Database migration from version 7 to 8.
 * Adds onboarded column to settings table for onboarding persistence.
 */
object Migration7To8 {
    val migration: Migration = object : Migration(7, 8) {
        override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE settings ADD COLUMN onboarded INTEGER NOT NULL DEFAULT 0")
        }
    }
}

/**
 * Database migration from version 8 to 9.
 * Adds amountsVisible column to settings table for global amount visibility toggle.
 */
object Migration8To9 {
    val migration: Migration = object : Migration(8, 9) {
        override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE settings ADD COLUMN amountsVisible INTEGER NOT NULL DEFAULT 1")
        }
    }
}

/**
 * Database migration from version 9 to 10.
 * Adds落点/专款 fields to asset_records and a new allocation_targets table
 * for sub-bucket (落点) level target / cap / stop-condition tracking.
 */
object Migration9To10 {
    val migration: Migration = object : Migration(9, 10) {
        override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
            // 落点标签 + 专款锁定
            database.execSQL("ALTER TABLE asset_records ADD COLUMN subCategory TEXT NOT NULL DEFAULT ''")
            database.execSQL("ALTER TABLE asset_records ADD COLUMN locked INTEGER NOT NULL DEFAULT 0")

            // 落点目标表
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS allocation_targets (
                    subCategory TEXT PRIMARY KEY NOT NULL,
                    riskBucket TEXT NOT NULL,
                    targetAmount REAL NOT NULL,
                    capAmount REAL NOT NULL DEFAULT 0,
                    stopNote TEXT NOT NULL DEFAULT '',
                    updatedAt INTEGER NOT NULL
                )
            """.trimIndent())
        }
    }
}

/**
 * Database migration from version 10 to 11.
 * Adds maxAggressiveRatio (永不满仓 · 风险仓位上限) to settings.
 */
object Migration10To11 {
    val migration: Migration = object : Migration(10, 11) {
        override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE settings ADD COLUMN maxAggressiveRatio REAL NOT NULL DEFAULT 0.7")
        }
    }
}

/** Database migration from version 11 to 12. Adds industry tag for holding redlines. */
object Migration11To12 {
    val migration: Migration = object : Migration(11, 12) {
        override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE asset_records ADD COLUMN industryTag TEXT NOT NULL DEFAULT ''")
        }
    }
}

/** Database migration from version 12 to 13. Adds manual QDII purchase restriction flag. */
object Migration12To13 {
    val migration: Migration = object : Migration(12, 13) {
        override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE asset_records ADD COLUMN purchaseRestricted INTEGER NOT NULL DEFAULT 0")
        }
    }
}

/** Database migration from version 13 to 14. Adds manual valuation and premium fields. */
object Migration13To14 {
    val migration: Migration = object : Migration(13, 14) {
        override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE asset_records ADD COLUMN peRatio REAL")
            database.execSQL("ALTER TABLE asset_records ADD COLUMN dividendYield REAL")
            database.execSQL("ALTER TABLE asset_records ADD COLUMN premiumRate REAL")
        }
    }
}

/** Database migration from version 14 to 15. Adds previousClose to prices for today's gain. */
object Migration14To15 {
    val migration: Migration = object : Migration(14, 15) {
        override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE prices ADD COLUMN previousClose REAL NOT NULL DEFAULT 0")
        }
    }
}

/** Database migration from version 15 to 16. Adds an explicit security code for holding aggregation. */
object Migration15To16 {
    val migration: Migration = object : Migration(15, 16) {
        override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE asset_records ADD COLUMN securityCode TEXT NOT NULL DEFAULT ''")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_asset_records_securityCode ON asset_records(securityCode)")
        }
    }
}

/**
 * v16 -> v17: unify active holdings on asset_records and add source/sync metadata.
 * The legacy positions table is retained for backwards compatibility, but its rows are copied once
 * into asset_records and then cleared so it cannot be counted a second time.
 */
object Migration16To17 {
    val migration: Migration = object : Migration(16, 17) {
        override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE accounts ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'MANUAL'")
            database.execSQL("ALTER TABLE accounts ADD COLUMN externalSourceId TEXT NOT NULL DEFAULT ''")
            database.execSQL("ALTER TABLE accounts ADD COLUMN lastSyncedAt INTEGER")
            database.execSQL("ALTER TABLE accounts ADD COLUMN syncState TEXT NOT NULL DEFAULT 'NOT_APPLICABLE'")

            database.execSQL("ALTER TABLE asset_records ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'MANUAL'")
            database.execSQL("ALTER TABLE asset_records ADD COLUMN sourceAccountId TEXT NOT NULL DEFAULT ''")
            database.execSQL("ALTER TABLE asset_records ADD COLUMN sourceRecordId TEXT NOT NULL DEFAULT ''")
            database.execSQL("ALTER TABLE asset_records ADD COLUMN importBatchId TEXT NOT NULL DEFAULT ''")
            database.execSQL("ALTER TABLE asset_records ADD COLUMN sourceFingerprint TEXT NOT NULL DEFAULT ''")
            database.execSQL("ALTER TABLE asset_records ADD COLUMN syncedAt INTEGER")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_asset_records_sourceFingerprint ON asset_records(sourceFingerprint)")

            database.execSQL("ALTER TABLE transactions ADD COLUMN origin TEXT NOT NULL DEFAULT 'TRADE'")

            database.execSQL(
                """
                INSERT INTO asset_records (
                    id, accountId, assetType, riskBucket, name, securityCode, quantity, cost,
                    currentPrice, currency, subCategory, industryTag, purchaseRestricted, peRatio,
                    dividendYield, premiumRate, locked, createdAt, updatedAt, sourceType,
                    sourceAccountId, sourceRecordId, importBatchId, sourceFingerprint, syncedAt
                )
                SELECT
                    p.id, p.accountId, 'STOCK', 'AGGRESSIVE', p.symbol, p.symbol, p.shares,
                    p.totalCost, CASE WHEN p.shares > 0 THEN p.totalCost / p.shares ELSE 0 END,
                    p.currency, '', '', 0, NULL, NULL, NULL, 0, p.createdAt, p.createdAt,
                    'LEGACY_MIGRATION', p.accountId, p.id, 'migration-v17',
                    'legacy:' || p.accountId || ':' || p.symbol, NULL
                FROM positions p
                WHERE NOT EXISTS (
                    SELECT 1 FROM asset_records ar
                    WHERE ar.accountId = p.accountId
                      AND (ar.securityCode = p.symbol OR (ar.securityCode = '' AND ar.name = p.symbol))
                )
                """.trimIndent()
            )
            database.execSQL("DELETE FROM positions")
        }
    }
}

/** v17 -> v18: add a stable external id to transaction events for retry-safe imports. */
object Migration17To18 {
    val migration: Migration = object : Migration(17, 18) {
        override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE transactions ADD COLUMN sourceFingerprint TEXT NOT NULL DEFAULT ''")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_sourceFingerprint ON transactions(sourceFingerprint)")
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
        Migration6To7.migration,
        Migration7To8.migration,
        Migration8To9.migration,
        Migration9To10.migration,
        Migration10To11.migration,
        Migration11To12.migration,
        Migration12To13.migration,
        Migration13To14.migration,
        Migration14To15.migration,
        Migration15To16.migration,
        Migration16To17.migration,
        Migration17To18.migration
    )
}
