package com.finunity.data.local

import androidx.room.TypeConverter
import com.finunity.data.local.entity.AccountType
import com.finunity.data.local.entity.AssetType
import com.finunity.data.local.entity.RiskBucket
import com.finunity.data.local.entity.TransactionType

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
}
