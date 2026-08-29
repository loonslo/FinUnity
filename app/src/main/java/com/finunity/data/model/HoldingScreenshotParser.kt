package com.finunity.data.model

/**
 * A holding parsed from the text of a brokerage/fund screenshot.
 *
 * A screenshot is a statement of the current position, rather than a transaction record.
 * In particular, [marketValue] must never be treated as the user's acquisition cost.
 */
data class ParsedScreenshotHolding(
    val name: String,
    val securityCode: String,
    val quantity: Double,
    val marketValue: Double,
    val currency: String,
    val needsReview: Boolean,
    val rawText: String = "",
    val rawSecurityCode: String = securityCode,
    val confidence: OcrConfidence = if (needsReview) OcrConfidence.MEDIUM else OcrConfidence.HIGH,
    val reviewReason: String? = null
) {
    val currentPrice: Double
        get() = if (quantity > 0.0) marketValue / quantity else 0.0
}

enum class OcrConfidence { HIGH, MEDIUM, LOW }

/**
 * Conservative parser for common Chinese brokerage holding-table layouts.
 *
 * ML Kit already supplies text in visual lines. A complete, valid-looking row is required before
 * it is offered for import; ambiguous rows are intentionally marked for confirmation in the UI.
 */
object HoldingScreenshotParser {
    private val securityCodeRegex = Regex(
        """(?i)(?<![A-Za-z0-9])(?:\d{4,6}(?:[._-]?(?:SH|SS|SZ|HK))?|[A-Z]{1,5}(?:\.[A-Z]{1,2})?)(?![A-Za-z0-9])"""
    )
    private val numberRegex = Regex("""[-+]?\d[\d,]*(?:\.\d+)?""")
    private val ignoredCodes = setOf("ETF", "USD", "HKD", "CNY", "RMB", "SH", "SZ")
    private val headerMarkers = listOf("证券名称", "股票名称", "基金名称", "证券代码", "持仓数量", "持有数量", "参考市值", "浮动盈亏")

    fun parse(text: String): List<ParsedScreenshotHolding> = text
        .lineSequence()
        .map { it.trim().replace('，', ',') }
        .filter { it.isNotBlank() && headerMarkers.none(it::contains) }
        .mapNotNull(::parseLine)
        .distinctBy { "${it.securityCode}|${it.currency}" }
        .toList()

    private fun parseLine(line: String): ParsedScreenshotHolding? {
        val candidates = securityCodeRegex.findAll(line)
            .filter { it.value.uppercase() !in ignoredCodes }
            .toList()
        val firstNumber = candidates.firstOrNull { it.value.all(Char::isDigit) }
        val alphabeticBeforeNumber = candidates
            .filter { it.value.any(Char::isLetter) && (firstNumber == null || it.range.first < firstNumber.range.first) }
        val codeMatch = candidates.firstOrNull { it.value.contains('.') && it.value.any(Char::isDigit) }
            ?: alphabeticBeforeNumber.lastOrNull()
            ?: firstNumber
            ?: candidates.lastOrNull()
            ?: return null
        val rawSecurityCode = codeMatch.value.uppercase()
        val normalized = normalizeSecurityCodeDetailed(rawSecurityCode)
        val code = normalized.code
        val name = line.substring(0, codeMatch.range.first)
            .trim()
            .trim('-', '·', '|', ':', '：')
            .ifBlank { code }
        val values = numberRegex.findAll(line.substring(codeMatch.range.last + 1))
            .mapNotNull { it.value.replace(",", "").toDoubleOrNull() }
            .filter { it >= 0.0 }
            .toList()

        // The overwhelming majority of holding tables place quantity before market value. We do
        // not guess a cost from other columns, and the confirmation screen keeps all numbers editable.
        val quantity = values.firstOrNull()?.takeIf { it > 0.0 } ?: return null
        val marketValue = values.lastOrNull()?.takeIf { it > 0.0 } ?: return null
        val reviewReasons = buildList {
            if (values.size != 2) add("识别到的数字列不止数量和市值")
            if (name == code) add("未识别到资产名称")
            normalized.reason?.let(::add)
        }
        val needsReview = reviewReasons.isNotEmpty()
        return ParsedScreenshotHolding(
            name = name,
            securityCode = code,
            quantity = quantity,
            marketValue = marketValue,
            currency = currencyFor(line),
            needsReview = needsReview,
            rawText = line,
            rawSecurityCode = rawSecurityCode,
            confidence = when {
                !needsReview -> OcrConfidence.HIGH
                values.size <= 3 -> OcrConfidence.MEDIUM
                else -> OcrConfidence.LOW
            },
            reviewReason = reviewReasons.joinToString("；").ifBlank { null }
        )
    }

    private fun currencyFor(line: String): String = when {
        line.contains("HK$") || line.contains("港币") || line.contains("HKD", ignoreCase = true) -> "HKD"
        line.contains("USD", ignoreCase = true) || line.contains("美元") || line.contains("US$") -> "USD"
        else -> "CNY"
    }
}
