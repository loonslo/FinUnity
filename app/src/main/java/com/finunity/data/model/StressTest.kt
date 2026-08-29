package com.finunity.data.model

import com.finunity.data.local.entity.RiskBucket

data class StressScenario(
    val id: String,
    val label: String,
    val drops: Map<RiskBucket, Double>
)

data class StressResult(
    val scenario: StressScenario,
    val lossRatio: Double,
    val lossAmount: Double,
    val withinTolerance: Boolean
)

val DEFAULT_STRESS_SCENARIOS = listOf(
    StressScenario("2022", "2022 股债双杀", mapOf(
        RiskBucket.AGGRESSIVE to 0.25, RiskBucket.BALANCED to 0.08,
        RiskBucket.DEFENSIVE to 0.0
    )),
    StressScenario("2020", "2020 疫情冲击", mapOf(
        RiskBucket.AGGRESSIVE to 0.35, RiskBucket.BALANCED to 0.08,
        RiskBucket.DEFENSIVE to 0.0
    )),
    StressScenario("2008", "2008 金融危机", mapOf(
        RiskBucket.AGGRESSIVE to 0.55, RiskBucket.BALANCED to 0.15,
        RiskBucket.DEFENSIVE to 0.0
    )),
    StressScenario("2000", "2000 科技泡沫", mapOf(
        RiskBucket.AGGRESSIVE to 0.50, RiskBucket.BALANCED to 0.10,
        RiskBucket.DEFENSIVE to 0.0
    ))
)

/** 压力浮亏 = 可投策略盘 × Σ(三桶权重 × 情景跌幅)。 */
fun computeStressLoss(
    strategyAssets: Double,
    weights: Map<RiskBucket, Double>,
    scenario: StressScenario,
    maxTolerableLoss: Double
): StressResult {
    val ratio = RiskBucket.entries.sumOf { bucket ->
        (weights[bucket] ?: 0.0).coerceAtLeast(0.0) *
            (scenario.drops[bucket] ?: 0.0).coerceIn(0.0, 1.0)
    }
    val amount = strategyAssets.coerceAtLeast(0.0) * ratio
    return StressResult(
        scenario = scenario,
        lossRatio = ratio,
        lossAmount = amount,
        withinTolerance = maxTolerableLoss > 0.0 && amount <= maxTolerableLoss + 0.01
    )
}
