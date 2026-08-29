package com.finunity.ui.screens

import java.text.NumberFormat
import java.util.Locale

/** Shared amount visibility state used by the current screens. */
object AmountVisibility {
    var visible: Boolean = true
}

fun formatCurrency(amount: Double, currency: String): String {
    if (!AmountVisibility.visible) return "••••"
    if (amount.isNaN() || amount.isInfinite()) return "--"
    val safeAmount = kotlin.math.abs(amount)
    val formatted = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(safeAmount)
    val symbol = when (currency) {
        "CNY" -> "¥"
        "USD" -> "\$"
        "HKD" -> "HK\$"
        else -> "$currency "
    }
    return (if (amount < 0) "-" else "") + symbol + formatted
}

fun formatSignedPercent(value: Double): String {
    val safeValue = if (value.isNaN() || value.isInfinite()) 0.0 else value
    return "${if (safeValue > 0) "+" else ""}${String.format(Locale.US, "%.1f", safeValue * 100)}%"
}

fun formatSignedMoney(amount: Double, currency: String): String = when {
    amount > 0 -> "+${formatCurrency(amount, currency)}"
    amount < 0 -> "-${formatCurrency(-amount, currency)}"
    else -> formatCurrency(0.0, currency)
}
