package com.finunity.data.model

import com.finunity.data.local.entity.AssetRecord
import com.finunity.data.local.entity.AssetType
import java.util.Locale

data class SignalEvaluation(
    val usdSecuritiesExposure: Double,
    val alerts: List<RiskAlert>
)

/** 评估可从本地资产数据直接得到的信号：美元证券敞口与手动限购。 */
fun evaluateSignalRules(records: List<AssetRecord>): SignalEvaluation {
    val tradable = records.filter { it.assetType in setOf(AssetType.STOCK, AssetType.ETF, AssetType.FUND) }
    val usdExposure = tradable.filter { it.currency.equals("USD", ignoreCase = true) }.sumOf { it.currentValue }
    val alerts = mutableListOf<RiskAlert>()

    when {
        usdExposure >= 60_000.0 -> alerts += RiskAlert(
            RiskAlertLevel.WARNING, "美元证券敞口已达 \$60K",
            "当前约 USD ${String.format(Locale.US, "%,.0f", usdExposure)}。税务与继承规则因居住地和资产注册地而异，建议尽快咨询专业人士。"
        )
        usdExposure >= 50_000.0 -> alerts += RiskAlert(
            RiskAlertLevel.WARNING, "美元证券敞口接近 \$60K",
            "当前约 USD ${String.format(Locale.US, "%,.0f", usdExposure)}，请提前复核跨境税务与继承安排。"
        )
        usdExposure >= 45_000.0 -> alerts += RiskAlert(
            RiskAlertLevel.INFO, "美元证券敞口进入关注区",
            "当前约 USD ${String.format(Locale.US, "%,.0f", usdExposure)}，距 \$50K/\$60K 提醒档位已较近。"
        )
    }

    tradable.filter { it.purchaseRestricted }.forEach {
        alerts += RiskAlert(
            RiskAlertLevel.WARNING,
            "${it.name}：当前限购",
            "暂停加仓，可保留定投资金或转向同三桶未超限落点。"
        )
    }
    tradable.filter { (it.premiumRate ?: 0.0) >= 0.03 }.forEach {
        alerts += RiskAlert(
            RiskAlertLevel.WARNING,
            "${it.name}：场内溢价过高",
            "手动记录溢价 ${String.format(Locale.US, "%.1f", (it.premiumRate ?: 0.0) * 100)}%，已达 3% 禁买线，建议不追高。"
        )
    }
    tradable.filter { it.subCategory.contains("红利") || it.name.contains("红利") }.forEach {
        val reasons = buildList {
            it.peRatio?.takeIf { pe -> pe > 15.0 }?.let { pe -> add("PE ${String.format(Locale.US, "%.1f", pe)} > 15") }
            it.dividendYield?.takeIf { yield -> yield < 0.03 }?.let { yield -> add("股息率 ${String.format(Locale.US, "%.1f", yield * 100)}% < 3%") }
        }
        if (reasons.isNotEmpty()) {
            alerts += RiskAlert(
                RiskAlertLevel.WARNING,
                "${it.name}：红利估值未达门槛",
                reasons.joinToString("、") + "。请复核数据后再决定是否加仓。"
            )
        }
    }
    return SignalEvaluation(usdExposure, alerts)
}
