package com.finunity.data.model

import com.finunity.data.local.entity.AssetRecord
import com.finunity.data.local.entity.AssetType
import com.finunity.data.local.entity.Position
import com.finunity.data.local.entity.RiskBucket

/**
 * 风险维度汇总
 */
data class RiskBucketSummary(
    val riskBucket: RiskBucket,
    val totalValue: Double,
    val recordCount: Int,
    val percentage: Double
)

/**
 * 统一的资产记录接口
 * 用于兼容旧的 Position 和新的 AssetRecord
 */
interface UnifiedAsset {
    val id: String
    val accountId: String
    val name: String
    val quantity: Double
    val cost: Double
    val currentPrice: Double
    val currency: String
    val assetType: AssetType
    val riskBucket: RiskBucket

    val currentValue: Double
        get() = quantity * currentPrice

    val profitLoss: Double
        get() = currentValue - cost

    val profitLossRatio: Double
        get() = if (cost > 0) profitLoss / cost else 0.0

    val averageCost: Double
        get() = if (quantity > 0) cost / quantity else 0.0
}

/**
 * Position 转 UnifiedAsset
 */
fun Position.toUnified(): UnifiedAsset = object : UnifiedAsset {
    override val id: String = this@toUnified.id
    override val accountId: String = this@toUnified.accountId
    override val name: String = this@toUnified.symbol
    override val quantity: Double = this@toUnified.shares
    override val cost: Double = this@toUnified.totalCost
    override val currentPrice: Double = this@toUnified.averageCost
    override val currency: String = this@toUnified.currency
    override val assetType: AssetType = AssetType.STOCK
    override val riskBucket: RiskBucket = RiskBucket.AGGRESSIVE
}

/**
 * AssetRecord 转 UnifiedAsset
 */
fun AssetRecord.toUnified(): UnifiedAsset = object : UnifiedAsset {
    override val id: String = this@toUnified.id
    override val accountId: String = this@toUnified.accountId
    override val name: String = this@toUnified.name
    override val quantity: Double = this@toUnified.quantity
    override val cost: Double = this@toUnified.cost
    override val currentPrice: Double = this@toUnified.currentPrice
    override val currency: String = this@toUnified.currency
    override val assetType: AssetType = this@toUnified.assetType
    override val riskBucket: RiskBucket = this@toUnified.riskBucket
}

/**
 * 合并 Position 和 AssetRecord 为统一列表
 */
fun mergeAssets(positions: List<Position>, records: List<AssetRecord>): List<UnifiedAsset> {
    val fromPositions = positions.map { it.toUnified() }
    val fromRecords = records.map { it.toUnified() }
    return fromPositions + fromRecords
}

/**
 * 按风险维度聚合
 */
fun groupByRiskBucket(assets: List<UnifiedAsset>): Map<RiskBucket, List<UnifiedAsset>> {
    return assets.groupBy { it.riskBucket }
}

/**
 * 计算风险维度汇总
 */
fun calculateRiskBucketSummaries(
    assets: List<UnifiedAsset>,
    totalValue: Double
): List<RiskBucketSummary> {
    val grouped = groupByRiskBucket(assets)
    return grouped.map { (bucket, items) ->
        val bucketTotal = items.sumOf { it.currentValue }
        RiskBucketSummary(
            riskBucket = bucket,
            totalValue = bucketTotal,
            recordCount = items.size,
            percentage = if (totalValue > 0) bucketTotal / totalValue else 0.0
        )
    }
}

/**
 * 风险维度名称映射
 */
fun RiskBucket.displayName(): String = when (this) {
    RiskBucket.CONSERVATIVE -> "稳健"
    RiskBucket.AGGRESSIVE -> "进取"
    RiskBucket.CASH -> "防守"
}

/**
 * 资产类型名称映射
 */
fun AssetType.displayName(): String = when (this) {
    AssetType.STOCK -> "股票"
    AssetType.ETF -> "ETF"
    AssetType.FUND -> "基金"
    AssetType.CASH -> "现金"
    AssetType.TIME_DEPOSIT -> "定期"
}
