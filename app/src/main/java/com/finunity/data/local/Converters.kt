package com.finunity.data.local

import androidx.room.TypeConverter
import com.finunity.data.local.entity.AccountType
import com.finunity.data.local.entity.AssetType
import com.finunity.data.local.entity.RiskBucket
import com.finunity.data.local.entity.TransactionType
import com.finunity.data.local.entity.HoldingSourceType
import com.finunity.data.local.entity.AccountSourceType
import com.finunity.data.local.entity.SyncState
import com.finunity.data.local.entity.TransactionOrigin

class Converters {

    @TypeConverter
    fun fromAccountType(value: AccountType): String {
        return value.name
    }

    @TypeConverter
    fun toAccountType(value: String): AccountType {
        return AccountType.valueOf(value)
    }

    @TypeConverter
    fun fromTransactionType(value: TransactionType): String {
        return value.name
    }

    @TypeConverter
    fun toTransactionType(value: String): TransactionType {
        return TransactionType.valueOf(value)
    }

    @TypeConverter
    fun fromAssetType(value: AssetType): String {
        return value.name
    }

    @TypeConverter
    fun toAssetType(value: String): AssetType {
        return AssetType.valueOf(value)
    }

    @TypeConverter
    fun fromRiskBucket(value: RiskBucket): String {
        return value.name
    }

    @TypeConverter
    fun toRiskBucket(value: String): RiskBucket {
        return RiskBucket.valueOf(value)
    }

    @TypeConverter
    fun fromHoldingSourceType(value: HoldingSourceType): String = value.name

    @TypeConverter
    fun toHoldingSourceType(value: String): HoldingSourceType = HoldingSourceType.valueOf(value)

    @TypeConverter
    fun fromAccountSourceType(value: AccountSourceType): String = value.name

    @TypeConverter
    fun toAccountSourceType(value: String): AccountSourceType = AccountSourceType.valueOf(value)

    @TypeConverter
    fun fromSyncState(value: SyncState): String = value.name

    @TypeConverter
    fun toSyncState(value: String): SyncState = SyncState.valueOf(value)

    @TypeConverter
    fun fromTransactionOrigin(value: TransactionOrigin): String = value.name

    @TypeConverter
    fun toTransactionOrigin(value: String): TransactionOrigin = TransactionOrigin.valueOf(value)
}
