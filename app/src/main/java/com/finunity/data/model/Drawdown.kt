package com.finunity.data.model

/** 自历史总资产高点的回撤阶梯建议。 */
data class DrawdownAdvice(
    val peakAssets: Double,
    val currentAssets: Double,
    val drawdownRatio: Double,
    val levelPercent: Int,
    val recurringMultiplier: Double,
    val fundingSource: String,
    val recommendedAmmoAmount: Double,
    val sampleCount: Int
) {
    val triggered: Boolean get() = levelPercent > 0
}

/**
 * 天玑法回撤阶梯。口径固定为历史 [historicalTotals] 与当前总资产的最高值。
 * 弹药按档位分批使用，避免一次打满。样本少于 2 时不给出触发建议。
 */
fun evaluateDrawdownLadder(
    currentAssets: Double,
    historicalTotals: List<Double>,
    availableAmmo: Double
): DrawdownAdvice? {
    val validHistory = historicalTotals.filter { it.isFinite() && it > 0.0 }
    if (currentAssets <= 0.0 || validHistory.size < 2) return null
    val peak = maxOf(currentAssets, validHistory.maxOrNull() ?: currentAssets)
    val drawdown = ((peak - currentAssets) / peak).coerceIn(0.0, 1.0)
    val (level, multiplier, source, ammoRatio) = when {
        drawdown >= 0.25 -> DrawdownTier(25, 2.0, "弹药为主 + 定投", 1.00)
        drawdown >= 0.20 -> DrawdownTier(20, 2.0, "弹药为主 + 定投", 0.75)
        drawdown >= 0.15 -> DrawdownTier(15, 1.5, "定投 + 分批弹药", 0.50)
        drawdown >= 0.10 -> DrawdownTier(10, 1.5, "定投 + 少量弹药", 0.25)
        drawdown >= 0.05 -> DrawdownTier(5, 1.0, "提高定投", 0.0)
        else -> DrawdownTier(0, 1.0, "常规定投", 0.0)
    }
    return DrawdownAdvice(
        peakAssets = peak,
        currentAssets = currentAssets,
        drawdownRatio = drawdown,
        levelPercent = level,
        recurringMultiplier = multiplier,
        fundingSource = source,
        recommendedAmmoAmount = availableAmmo.coerceAtLeast(0.0) * ammoRatio,
        sampleCount = validHistory.size
    )
}

private data class DrawdownTier(
    val level: Int,
    val multiplier: Double,
    val source: String,
    val ammoRatio: Double
)
