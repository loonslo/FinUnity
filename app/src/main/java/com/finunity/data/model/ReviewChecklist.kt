package com.finunity.data.model

enum class ReviewCadence(val label: String) { MONTHLY("月度"), QUARTERLY("季度"), YEARLY("年度") }

data class ReviewChecklistItem(
    val id: String,
    val cadence: ReviewCadence,
    val text: String,
    val automaticPassed: Boolean? = null,
    val detail: String = ""
)

fun buildReviewChecklist(summary: PortfolioSummary): List<ReviewChecklistItem> {
    val cashRatio = summary.allocations["CASH"] ?: 0.0
    val cashTarget = com.finunity.data.local.entity.parseTargetAllocation(summary.targetAllocation)["CASH"] ?: 0.0
    val trainingWarning = summary.riskAlerts.any { it.title.contains("训练仓") && it.level == RiskAlertLevel.WARNING }
    val ammo = summary.landingPoints.firstOrNull { it.subCategory.trim() == "弹药" }?.currentValue ?: 0.0
    val redlineWarnings = summary.riskAlerts.count { it.level == RiskAlertLevel.WARNING }

    return listOf(
        ReviewChecklistItem("defense", ReviewCadence.MONTHLY, "防守资金是否充足", cashRatio + summary.rebalanceThreshold >= cashTarget,
            "当前防守 ${(cashRatio * 100).toInt()}%，目标 ${(cashTarget * 100).toInt()}%"),
        ReviewChecklistItem("risk_cap", ReviewCadence.MONTHLY, "风险仓位是否低于上限", summary.aggressiveRatio <= summary.maxAggressiveRatio + 1e-9),
        ReviewChecklistItem("landing", ReviewCadence.MONTHLY, "各落点是否未超上限", summary.landingPoints.none { it.overCap }),
        ReviewChecklistItem("training", ReviewCadence.MONTHLY, "训练仓是否不超过 6 万", !trainingWarning),
        ReviewChecklistItem("ammo", ReviewCadence.MONTHLY, "回撤弹药是否仍在", ammo > 0.0,
            if (ammo > 0.0) "当前弹药 ${summary.baseCurrency} ${String.format("%,.0f", ammo)}" else "未找到「弹药」落点"),
        ReviewChecklistItem("overseas_hold", ReviewCadence.MONTHLY, "境外核心仓是否保持只增不减"),
        ReviewChecklistItem("redlines", ReviewCadence.QUARTERLY, "标的与行业红线是否全部复核", redlineWarnings == 0,
            if (redlineWarnings == 0) "未触发警告" else "当前 $redlineWarnings 条风险警告"),
        ReviewChecklistItem("targets", ReviewCadence.QUARTERLY, "落点目标、上限和停止条件是否仍合适"),
        ReviewChecklistItem("stress", ReviewCadence.YEARLY, "是否重做压力测试并确认最大可承受亏损 M"),
        ReviewChecklistItem("life", ReviewCadence.YEARLY, "家庭目标、现金底线与专款隔离是否需要更新")
    )
}
