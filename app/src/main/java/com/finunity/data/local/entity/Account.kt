package com.finunity.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * 账户实体
 * 支持证券、银行、现金等不同类型账户
 */
@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,                    // 账户名称，如"富途-港股"
    val type: AccountType,              // BROKER, BANK, CASH_MANAGEMENT, OTHER
    val currency: String,                // CNY, USD, HKD
    val balance: Double,                // 仅负债账户使用；普通账户金额由 AssetRecord 表达
    val createdAt: Long = System.currentTimeMillis()
)

enum class AccountType {
    BROKER,       // 券商账户
    BANK,         // 银行账户
    FUND,         // 基金账户
    CASH_MANAGEMENT,  // 现金管理（如余额宝）
    BOND,         // 债券账户
    INSURANCE,    // 保险账户
    LIABILITY,    // 负债账户
    OTHER         // 其他
}

fun AccountType.displayName(): String = when (this) {
    AccountType.BROKER -> "证券账户"
    AccountType.BANK -> "银行账户"
    AccountType.FUND -> "基金平台"
    AccountType.CASH_MANAGEMENT -> "现金账户"
    AccountType.BOND -> "债券托管"
    AccountType.INSURANCE -> "保险账户"
    AccountType.LIABILITY -> "负债账户"
    AccountType.OTHER -> "其他账户"
}
