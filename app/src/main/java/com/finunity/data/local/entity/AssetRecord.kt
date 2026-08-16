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
    indices = [Index(value = ["accountId"]), Index(value = ["assetType"]), Index(value = ["securityCode"])]
)
data class AssetRecord(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val accountId: String,                    // 关联账户ID
    val assetType: AssetType,                 // 资产类型：股票、ETF、基金、现金、定期存款
    val riskBucket: RiskBucket,               // 风险维度：稳健、进取、防守
    val name: String,                          // 展示名称，如"沪深300ETF"或"余额宝"
    val securityCode: String = "",            // 证券编码；同码归集和交易流水匹配的稳定键
    val quantity: Double,                      // 数量/份额
    val cost: Double,                         // 买入成本
    val currentPrice: Double,                 // 当前价格/净值
    val currency: String,                     // 币种：CNY, USD, HKD
    val subCategory: String = "",             // 子类/落点标签，如"标普500""纳指100""红利""训练仓""弹药""生存层"。空=未归落点
    val industryTag: String = "",             // 行业/产业链标签，用于单一行业暴露红线
    val purchaseRestricted: Boolean = false,  // QDII/基金等是否处于限购（手动维护）
    val peRatio: Double? = null,              // 市盈率（手动输入）
    val dividendYield: Double? = null,        // 股息率，小数表示（手动输入）
    val premiumRate: Double? = null,          // 场内溢价率，小数表示（手动输入）
    val locked: Boolean = false,              // 锁定专款（生存层/嫁妆等），不计入可投策略盘、不参与再平衡建议
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
    STOCK,            // 股票
    ETF,              // 交易所交易基金
    FUND,             // 基金
    CASH,             // 现金/活期
    TIME_DEPOSIT,     // 定期存款
    REAL_ESTATE,      // 房产
    VEHICLE,          // 车辆
    INSURANCE_POLICY  // 保单/年金
}

/**
 * 风险维度枚举（标普四象限）
 * - CASH（要花的钱 / 防守）：活期、余额宝等随时可用的钱
 * - INSURANCE（保命的钱 / 保命）：保险、应急保障，专款专用不轻易动用
 * - CONSERVATIVE（保本的钱 / 稳健）：定期、债券、货币基金等低波动资产
 * - AGGRESSIVE（生钱的钱 / 进取）：股票、ETF、股票型基金等高风险资产
 */
enum class RiskBucket {
    CONSERVATIVE,  // 稳健型：定期存款、债券、货币基金等低风险资产
    AGGRESSIVE,    // 进取型：股票、ETF、股票型基金等高风险资产
    INSURANCE,     // 保命型：保险、应急保障资金
    CASH           // 防守型：活期存款、余额宝等随时可用的资金
}
