package com.finunity.data.local

import androidx.room.TypeConverter
import com.finunity.data.local.entity.AccountType
import com.finunity.data.local.entity.AssetType
import com.finunity.data.local.entity.RiskBucket
import com.finunity.data.local.entity.requireThreeBucket
import com.finunity.data.local.entity.TransactionType
import com.finunity.data.local.entity.HoldingSourceType
import com.finunity.data.local.entity.AccountSourceType
import com.finunity.data.local.entity.SyncState
import com.finunity.data.local.entity.TransactionOrigin
import com.finunity.data.local.entity.CashFlowCategory
import com.finunity.data.local.entity.RecurringRuleType

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
        return requireThreeBucket(value)
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

    @TypeConverter
    fun fromCashFlowCategory(value: CashFlowCategory): String = value.name

    @TypeConverter
    fun toCashFlowCategory(value: String): CashFlowCategory =
        runCatching { CashFlowCategory.valueOf(value) }.getOrDefault(CashFlowCategory.OTHER)

    @TypeConverter
    fun fromRecurringRuleType(value: RecurringRuleType): String = value.name

    @TypeConverter
    fun toRecurringRuleType(value: String): RecurringRuleType = RecurringRuleType.valueOf(value)
}
