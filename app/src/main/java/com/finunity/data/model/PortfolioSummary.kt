package com.finunity.data.model

import com.finunity.data.local.entity.Account
import com.finunity.data.local.entity.AssetRecord
import com.finunity.data.local.entity.Position
import com.finunity.data.local.entity.RiskBucket

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
    val landingPoints: List<LandingPoint> = emptyList(), // 落点跟踪（子桶级目标 vs 现有）
    val lockedAssets: Double = 0.0,    // 锁定专款合计（生存层/嫁妆等），不计入可投策略盘
    val maxAggressiveRatio: Double = 0.70, // 永不满仓 · 风险仓位上限（进取占比）
    val lastUpdated: Long             // 最后更新时间
) {
    /** 可投策略盘 = 总资产 − 锁定专款 */
    val strategyAssets: Double get() = totalAssets - lockedAssets

    /** 风险仓位 = 进取（生钱的钱）占比 0.0–1.0 */
    val aggressiveRatio: Double get() = allocations["AGGRESSIVE"] ?: 0.0

    /** 风险体检告警（永不满仓 + 落点红线 + 达标提示） */
    val riskAlerts: List<RiskAlert> get() = evaluateRiskAlerts(aggressiveRatio, maxAggressiveRatio, landingPoints)
}

/** 风险体检告警等级 */
enum class RiskAlertLevel { WARNING, INFO }

/** 风险体检单条告警 */
data class RiskAlert(
    val level: RiskAlertLevel,
    val title: String,
    val detail: String
)

/**
 * 风险体检：把方案红线集中判定，返回告警列表（纯函数，便于单测）。
 * - 永不满仓：进取占比超过上限 → WARNING
 * - 落点超上限红线 → WARNING（每个落点一条）
 * - 落点已达目标（未超限）→ INFO（提示可停止加仓）
 */
fun evaluateRiskAlerts(
    aggressiveRatio: Double,
    maxAggressiveRatio: Double,
    landingPoints: List<LandingPoint>
): List<RiskAlert> {
    val alerts = mutableListOf<RiskAlert>()

    if (maxAggressiveRatio in 0.0..1.0 && aggressiveRatio > maxAggressiveRatio + 1e-9) {
        val cur = Math.round(aggressiveRatio * 100).toInt()
        val cap = Math.round(maxAggressiveRatio * 100).toInt()
        alerts += RiskAlert(
            RiskAlertLevel.WARNING,
            "风险仓位偏高",
            "进取（生钱的钱）已占 $cur%，超过上限 $cap%。建议新钱先进防守/稳健，暂不追加进取。"
        )
    }

    landingPoints.filter { it.overCap }.forEach {
        alerts += RiskAlert(
            RiskAlertLevel.WARNING,
            "${it.subCategory}：已超上限",
            "已超过你为该落点设的上限红线，建议不再加仓。"
        )
    }

    landingPoints.filter { it.reachedTarget && !it.overCap }.forEach {
        alerts += RiskAlert(
            RiskAlertLevel.INFO,
            "${it.subCategory}：已达目标",
            it.stopNote.ifBlank { "已到目标金额，可停止加仓、转为再平衡。" }
        )
    }

    return alerts
}

/**
 * 落点跟踪行（对应方案第三章加仓落点表的一行）
 * 把"四象限之下"的具体落点（标普/纳指/红利/训练仓/弹药…）的目标、现有、缺口、红线汇总。
 */
data class LandingPoint(
    val subCategory: String,          // 落点名称
    val riskBucket: RiskBucket,       // 所属象限
    val currentValue: Double,         // 现有市值（基准货币）
    val targetAmount: Double,         // 目标金额，0 表示未设目标（有持仓但未归落点目标）
    val capAmount: Double,            // 上限红线，0 表示不设
    val stopNote: String,             // 停止条件
    val hasTarget: Boolean            // 是否已设置目标（区分"未跟踪"落点）
) {
    /** 缺口：>0 还需买入，<0 已超配 */
    val gap: Double get() = targetAmount - currentValue

    /** 完成进度 0.0–1.0（无目标时为 0） */
    val progress: Double get() = if (targetAmount > 0) (currentValue / targetAmount).coerceIn(0.0, 1.0) else 0.0

    /** 是否触及/超过上限红线 */
    val overCap: Boolean get() = capAmount > 0 && currentValue > capAmount + 0.01

    /** 是否已达成目标（可停止加仓） */
    val reachedTarget: Boolean get() = targetAmount > 0 && currentValue >= targetAmount - 0.01
}

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
