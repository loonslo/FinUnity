package com.finunity.data.model

import com.finunity.data.local.entity.RiskBucket
import java.util.Locale

/** 证券代码标准化结果；裸 A 股代码无法从文本可靠推断交易所时必须提示确认。 */
data class NormalizedSecurityCode(
    val code: String,
    val needsConfirmation: Boolean = false,
    val reason: String? = null
)

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
    return normalizeSecurityCodeDetailed(trimmed).code
}

/**
 * 规范化 OCR、CSV 和手动录入的证券代码。
 * Yahoo 对 A 股使用六位代码加 .SS/.SZ，对港股使用四位代码加 .HK；
 * 只有裸六位数字时不猜交易所，交给上层显示“需确认”。
 */
fun normalizeSecurityCodeDetailed(value: String): NormalizedSecurityCode {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return NormalizedSecurityCode("未命名资产", true, "缺少证券代码")

    val suffix = Regex("(?i)(?:[._-]?(SH|SS|SZ|HK))\\s*$").find(trimmed)?.groupValues?.getOrNull(1)?.uppercase(Locale.ROOT)
    val numericCode = Regex("(?<!\\d)\\d{4,6}(?!\\d)").findAll(trimmed)
        .maxByOrNull { it.value.length }
        ?.value
    val ticker = Regex("(?i)(?<![A-Z])[A-Z]{1,5}(?:[._-][A-Z]{1,4})?(?![A-Z])").find(trimmed)?.value

    if (numericCode != null && suffix != null) {
        val normalized = when (suffix) {
            "SH", "SS", "SZ" -> numericCode.padStart(6, '0') + "." + if (suffix == "SH") "SS" else suffix
            "HK" -> numericCode.takeLast(4).padStart(4, '0') + ".HK"
            else -> numericCode
        }
        return NormalizedSecurityCode(normalized)
    }

    if (numericCode != null) {
        val normalized = numericCode.uppercase(Locale.ROOT)
        if (normalized.length in 4..5) {
            // 港股代码在截图中经常没有 .HK；四位规范形式保留前导零。
            return NormalizedSecurityCode(normalized.takeLast(4).padStart(4, '0') + ".HK")
        }
        return NormalizedSecurityCode(normalized, normalized.length == 6, "裸 A 股代码无法判断上海/深圳交易所")
    }

    if (ticker != null) {
        return NormalizedSecurityCode(ticker.uppercase(Locale.ROOT))
    }

    return NormalizedSecurityCode(trimmed.uppercase(Locale.ROOT).ifBlank { "未命名资产" }, true, "无法识别证券代码")
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
                // 同码跨币种时不能合成一个“单价”；详情页按来源分别展示。
                currentPrice = if (currencies.size == 1 && totalQuantity > 0.0) currentValue / totalQuantity else 0.0,
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
