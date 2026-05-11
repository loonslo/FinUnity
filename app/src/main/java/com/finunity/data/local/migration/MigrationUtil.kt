package com.finunity.data.local.migration

/**
 * Migration strategies for database version upgrades.
 *
 * Version history:
 * - Version 1: Initial schema (Account, Position, Price)
 * - Version 2: Added Settings, Transaction tables
 * - Version 3: Added AssetSnapshot table
 * - Version 4+: Uses fallbackToDestructiveMigrationFrom(1, 2) for v1/v2,
 *               requires explicit migrations for v3+
 *
 * For production:
 * - Define explicit migrations in Migration1To2, Migration2To3, etc.
 * - Use AutoMigrations where possible for simpler schema changes
 * - Always test migrations with actual data before releasing
 */
object MigrationUtil {

    /**
     * Validates that the migration can be applied safely.
     * Called before applying destructive migrations.
     */
    fun validateMigrationSteps() {
        // TODO: Add validation logic before destructive migration
        // - Check for unmigrated data that should be preserved
        // - Verify backup exists for critical user data
    }
}