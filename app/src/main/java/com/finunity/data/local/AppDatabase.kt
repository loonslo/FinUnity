package com.finunity.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import com.finunity.data.local.dao.AccountDao
import com.finunity.data.local.dao.AssetRecordDao
import com.finunity.data.local.dao.AssetSnapshotDao
import com.finunity.data.local.dao.PositionDao
import com.finunity.data.local.dao.PriceDao
import com.finunity.data.local.dao.PriceHistoryDao
import com.finunity.data.local.dao.SettingsDao
import com.finunity.data.local.dao.TransactionDao
import com.finunity.data.local.entity.Account
import com.finunity.data.local.entity.AccountType
import com.finunity.data.local.entity.AssetRecord
import com.finunity.data.local.entity.AssetSnapshot
import com.finunity.data.local.entity.Position
import com.finunity.data.local.entity.Price
import com.finunity.data.local.entity.PriceHistory
import com.finunity.data.local.entity.Settings
import com.finunity.data.local.entity.Transaction
import com.finunity.data.local.migration.DatabaseMigrations

@Database(
    entities = [Account::class, Position::class, Price::class, Settings::class, Transaction::class, AssetSnapshot::class, AssetRecord::class, PriceHistory::class],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun positionDao(): PositionDao
    abstract fun priceDao(): PriceDao
    abstract fun settingsDao(): SettingsDao
    abstract fun transactionDao(): TransactionDao
    abstract fun assetSnapshotDao(): AssetSnapshotDao
    abstract fun assetRecordDao(): AssetRecordDao
    abstract fun priceHistoryDao(): PriceHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "finunity_database"
                )
                    // 从版本1、2升级时允许破坏性迁移（无用户数据或测试环境）
                    // 从版本3升级必须使用显式迁移
                    .fallbackToDestructiveMigrationFrom(1, 2)
                    .addMigrations(*DatabaseMigrations.ALL_MIGRATIONS.toTypedArray())
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
