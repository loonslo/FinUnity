package com.finunity.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * 账户实体
 * 支持券商、银行、基金等不同类型账户
 */
@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,                    // 账户名称，如"富途-港股"
    val type: AccountType,              // BROKER, BANK, FUND, OTHER
    val currency: String,                // CNY, USD, HKD
    val balance: Double,                // 现金余额
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
