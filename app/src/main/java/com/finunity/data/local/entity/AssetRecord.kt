package com.finunity.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * 资产记录实体
 * 账户下的具体资产记录，支持多种资产类型
 */
@Entity(
    tableName = "asset_records",
    foreignKeys = [
        ForeignKey(
            entity = Account::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["accountId"]), Index(value = ["assetType"])]
)
data class AssetRecord(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val accountId: String,                    // 关联账户ID
    val assetType: AssetType,                 // 资产类型：股票、ETF、基金、现金、定期存款
    val riskBucket: RiskBucket,               // 风险维度：稳健、进攻、现金
    val name: String,                          // 名称/代码，如"AAPL"或"余额宝"
    val quantity: Double,                      // 数量/份额
    val cost: Double,                         // 买入成本
    val currentPrice: Double,                 // 当前价格/净值
    val currency: String,                     // 币种：CNY, USD, HKD
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * 当前市值 = 数量 * 当前价格
     */
    val currentValue: Double
        get() = quantity * currentPrice

    /**
     * 盈亏 = 当前市值 - 成本
     */
    val profitLoss: Double
        get() = currentValue - cost

    /**
     * 盈亏比例
     */
    val profitLossRatio: Double
        get() = if (cost > 0) profitLoss / cost else 0.0

    /**
     * 平均成本（单价）
     */
    val averageCost: Double
        get() = if (quantity > 0) cost / quantity else 0.0
}

/**
 * 资产类型枚举
 */
enum class AssetType {
    STOCK,       // 股票
    ETF,         // 交易所交易基金
    FUND,        // 基金
    CASH,        // 现金/活期
    TIME_DEPOSIT // 定期存款
}

/**
 * 风险维度枚举
 */
enum class RiskBucket {
    CONSERVATIVE,  // 稳健型：现金、定期存款、货币基金等低风险资产
    AGGRESSIVE,    // 进攻型：股票、ETF、股票型基金等高风险资产
    CASH          // 现金类：活期存款、余额宝等随时可用的资金
}
