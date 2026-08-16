package com.finunity.data.model

/** 账户内单条原始持仓的数量与总成本。 */
data class HoldingCostState(
    val quantity: Double,
    val totalCost: Double
)

sealed interface HoldingTradeCalculation {
    data class Success(val state: HoldingCostState?) : HoldingTradeCalculation
    data class Error(val message: String) : HoldingTradeCalculation
}

/**
 * 交易流水驱动持仓的纯计算规则。
 * 买入采用加权总成本；卖出采用平均成本法按比例结转，卖完时返回 null。
 */
fun calculateHoldingTrade(
    existing: HoldingCostState?,
    isBuy: Boolean,
    quantity: Double,
    price: Double
): HoldingTradeCalculation {
    if (quantity <= 0.0 || price <= 0.0) {
        return HoldingTradeCalculation.Error("数量和成交价必须大于 0")
    }
    if (isBuy) {
        val current = existing ?: HoldingCostState(0.0, 0.0)
        return HoldingTradeCalculation.Success(
            HoldingCostState(
                quantity = current.quantity + quantity,
                totalCost = current.totalCost + quantity * price
            )
        )
    }
    if (existing == null) return HoldingTradeCalculation.Error("没有可卖持仓")
    if (quantity > existing.quantity) return HoldingTradeCalculation.Error("超出可卖数量")

    val remainingQuantity = existing.quantity - quantity
    return HoldingTradeCalculation.Success(
        if (remainingQuantity <= 0.0000001) null
        else HoldingCostState(
            quantity = remainingQuantity,
            totalCost = existing.totalCost * remainingQuantity / existing.quantity
        )
    )
}
