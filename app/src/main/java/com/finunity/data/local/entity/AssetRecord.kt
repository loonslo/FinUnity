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
    indices = [Index(value = ["accountId"]), Index(value = ["assetType"]), Index(value = ["securityCode"]), Index(value = ["instrumentId"])]
)
data class AssetRecord(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val accountId: String,                    // 关联账户ID
    val assetType: AssetType,                 // 资产类型：股票、ETF、基金、现金、定期存款
    val riskBucket: RiskBucket,               // 风险维度：稳健、进取、防守
    val name: String,                          // 展示名称，如"沪深300ETF"或"余额宝"
    val securityCode: String = "",            // 证券编码；同码归集和交易流水匹配的稳定键
    val instrumentId: String = "",             // FinUnity 专属服务的稳定证券 ID
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
    val updatedAt: Long = System.currentTimeMillis(),
    val sourceType: HoldingSourceType = HoldingSourceType.MANUAL,
    val sourceAccountId: String = "",
    val sourceRecordId: String = "",
    val importBatchId: String = "",
    val sourceFingerprint: String = "",
    val syncedAt: Long? = null
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

/** 正式产品使用的三桶。旧数据库值通过 [legacyBucketToThreeBucket] 迁移到这里。 */
enum class RiskBucket {
    DEFENSIVE,     // 防守：现金、活期和近期备用金
    BALANCED,      // 稳健：定期、债券、保险、房产、车辆和低波动资产
    AGGRESSIVE     // 进攻：股票、ETF、权益基金和训练仓
}

/**
 * 将数据库、备份或 CSV 中的旧四桶键转换为正式三桶键。
 * 返回 null 表示输入不是受支持的桶键，调用方应保留原始值并报告诊断信息。
 */
fun legacyBucketToThreeBucket(value: String): RiskBucket? = when (value.trim().uppercase()) {
    "DEFENSIVE", "防守", "CASH" -> RiskBucket.DEFENSIVE
    "BALANCED", "稳健", "BALANCE", "CONSERVATIVE", "INSURANCE", "保命" -> RiskBucket.BALANCED
    "AGGRESSIVE", "进攻" -> RiskBucket.AGGRESSIVE
    else -> null
}

/** 对持久化层使用的风险桶做严格解析，未知值必须显式失败。 */
fun requireThreeBucket(value: String, fieldName: String = "riskBucket"): RiskBucket =
    legacyBucketToThreeBucket(value) ?: throw IllegalArgumentException(
        "Unknown $fieldName '$value'; expected DEFENSIVE, BALANCED or AGGRESSIVE"
    )

/** 资产类型的默认桶。基金没有子类型时归入稳健，避免无提示地承担进攻风险。 */
fun AssetType.defaultRiskBucket(): RiskBucket = when (this) {
    AssetType.CASH -> RiskBucket.DEFENSIVE
    AssetType.TIME_DEPOSIT,
    AssetType.REAL_ESTATE,
    AssetType.VEHICLE,
    AssetType.INSURANCE_POLICY,
    AssetType.FUND -> RiskBucket.BALANCED
    AssetType.STOCK,
    AssetType.ETF -> RiskBucket.AGGRESSIVE
}
