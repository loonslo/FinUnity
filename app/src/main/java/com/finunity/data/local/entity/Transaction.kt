package com.finunity.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * 交易流水
 * 记录每一笔买入、卖出、转账、分红、手续费
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = Account::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["accountId"]), Index(value = ["symbol"]), Index(value = ["recordId"])]
)
data class Transaction(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val accountId: String,           // 关联账户
    val symbol: String?,             // 股票代码（null 表示非股票交易如转账）
    val type: TransactionType,       // 交易类型
    val shares: Double?,             // 股数（股票交易）
    val price: Double?,              // 单价（股票交易）
    val amount: Double,              // 总金额
    val currency: String,            // 币种
    val timestamp: Long = System.currentTimeMillis(),
    val note: String? = null,        // 备注
    val recordId: String? = null,    // 关联的资产记录 ID（可选，用于精确追溯）
    val balanceAfter: Double? = null // 交易后余额（用于审计追溯，可为空表示未记录）
)

enum class TransactionType {
    BUY,      // 买入
    SELL,     // 卖出
    DIVIDEND, // 分红
    FEE,      // 手续费
    TRANSFER_IN,  // 转入
    TRANSFER_OUT, // 转出
    DEPOSIT,  // 入金
    WITHDRAW  // 出金
}
