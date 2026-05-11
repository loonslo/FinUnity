package com.finunity.data.model

import com.finunity.data.local.entity.Account
import com.finunity.data.local.entity.AssetRecord
import com.finunity.data.local.entity.Position

/**
 * 资产汇总数据
 * 用于在 MainScreen 显示
 */
data class PortfolioSummary(
    val totalAssets: Double,           // 总资产（基准货币）
    val cashAssets: Double,            // 现金资产
    val stockAssets: Double,          // 股票资产（含基金、ETF）
    val stockRatio: Double,            // 股票占比 0.0-1.0
    val baseCurrency: String,          // 基准货币
    val rebalanceThreshold: Double,   // 再平衡阈值
    val needsRebalance: Boolean,        // 是否需要再平衡
    val targetAllocation: String,      // 目标配置（风险维度）
    val allocations: Map<String, Double>, // 当前各风险维度配置
    val rebalanceRecommendations: List<String>, // 再平衡建议
    val accounts: List<AccountSummary>,
    val riskBuckets: List<RiskBucketSummary>,
    val assetRecords: List<AssetRecordSummary>,
    val holdings: List<HoldingSummary>,
    val positions: List<PositionSummary>,
    val lastUpdated: Long             // 最后更新时间
)

/**
 * 账户汇总
 */
data class AccountSummary(
    val account: Account,
    val balanceInBaseCurrency: Double  // 换算后的余额
)

/**
 * 新资产记录明细
 */
data class AssetRecordSummary(
    val record: AssetRecord,
    val accountName: String,
    val currentValue: Double,
    val costInBaseCurrency: Double,
    val profitLoss: Double,
    val profitLossRatio: Double
)

/**
 * 持仓汇总（按股票代码合并）
 */
data class PositionSummary(
    val symbol: String,                // 股票代码
    val totalShares: Double,           // 总股数
    val averageCost: Double,           // 平均成本
    val totalCost: Double,             // 总成本
    val currentPrice: Double,          // 当前价格
    val currentValue: Double,          // 当前市值
    val profitLoss: Double,            // 盈亏金额
    val profitLossRatio: Double,       // 盈亏比例
    val currency: String               // 股票计价货币
)

/**
 * 具体持仓明细（按原始持仓记录）
 */
data class HoldingSummary(
    val position: Position,
    val accountName: String,
    val currentPrice: Double,
    val currentValue: Double,
    val profitLoss: Double,
    val profitLossRatio: Double
)
