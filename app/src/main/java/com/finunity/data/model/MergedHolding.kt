package com.finunity.data.model

import com.finunity.data.local.entity.RiskBucket
import java.util.Locale

/**
 * 进入统一合并管线前的单条持仓输入。
 * value/cost 已经换算为本位币，避免合并层重复处理汇率。
 */
data class HoldingMergeInput(
    val codeOrName: String,
    val displayName: String,
    val quantity: Double,
    val costInBaseCurrency: Double,
    val currentValueInBaseCurrency: Double,
    val accountId: String,
    val accountName: String,
    val currency: String,
    val riskBucket: RiskBucket
)

/**
 * 按证券编码合并后的统一持仓。
 * 一个编码只输出一行；成本、现值、数量均为所有来源的合计。
 */
data class MergedHoldingSummary(
    val code: String,
    val displayName: String,
    val totalQuantity: Double,
    val totalCost: Double,
    val currentPrice: Double,
    val currentValue: Double,
    val profitLoss: Double,
    val profitLossRatio: Double,
    val currency: String,
    val riskBucket: RiskBucket,
    val accountCount: Int,
    val sourceCount: Int,
    val accountIds: List<String>,
    val accountNames: List<String>
) {
    /** 合并后的加权平均成本，单位为本位币 / 数量。 */
    val averageCost: Double
        get() = if (totalQuantity > 0.0) totalCost / totalQuantity else 0.0
}

/**
 * 从“名称 / 编码”中提取稳定的合并键。
 * 例如“沪深300ETF · 510300”和“510300”会归到同一个编码。
 */
fun normalizeSecurityCode(value: String): String {
    val trimmed = value.trim()
    val code = Regex("(?<!\\d)\\d{4,6}(?!\\d)").find(trimmed)?.value
        ?: Regex("(?i)[A-Z]{1,4}\\d{4,}").find(trimmed)?.value
    val canonicalCode = code?.let {
        // 港股常见 0700 / 00700 两种写法，统一去掉前导 0；A 股六位代码保持原样。
        if (it.length < 6 && it.all { char -> char.isDigit() }) it.trimStart('0').ifBlank { "0" } else it
    }
    return (canonicalCode ?: trimmed).uppercase(Locale.ROOT).ifBlank { "未命名资产" }
}

/**
 * 纯函数合并入口，便于单元测试，也避免 UI 自己重复计算合并结果。
 */
fun mergeHoldingInputs(inputs: List<HoldingMergeInput>): List<MergedHoldingSummary> {
    return inputs.groupBy { normalizeSecurityCode(it.codeOrName) }
        .map { (code, rows) ->
            val totalQuantity = rows.sumOf { it.quantity }
            val totalCost = rows.sumOf { it.costInBaseCurrency }
            val currentValue = rows.sumOf { it.currentValueInBaseCurrency }
            val profitLoss = currentValue - totalCost
            val bucket = rows
                .groupBy { it.riskBucket }
                .maxByOrNull { (_, bucketRows) -> bucketRows.sumOf { it.currentValueInBaseCurrency } }
                ?.key
                ?: RiskBucket.AGGRESSIVE
            val currencies = rows.map { it.currency }.distinct()

            MergedHoldingSummary(
                code = code,
                displayName = rows.firstOrNull { it.displayName.isNotBlank() }?.displayName ?: code,
                totalQuantity = totalQuantity,
                totalCost = totalCost,
                currentPrice = if (totalQuantity > 0.0) currentValue / totalQuantity else 0.0,
                currentValue = currentValue,
                profitLoss = profitLoss,
                profitLossRatio = if (totalCost > 0.0) profitLoss / totalCost else 0.0,
                currency = currencies.singleOrNull() ?: "MIXED",
                riskBucket = bucket,
                accountCount = rows.map { it.accountId }.distinct().size,
                sourceCount = rows.size,
                accountIds = rows.map { it.accountId }.distinct(),
                accountNames = rows.map { it.accountName }.filter { it.isNotBlank() }.distinct()
            )
        }
        .sortedByDescending { it.currentValue }
}
