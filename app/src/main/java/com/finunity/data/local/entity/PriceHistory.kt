package com.finunity.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * 价格历史记录
 * 用于记录股票/ETF/基金的买入价和当前净值变化历史
 */
@Entity(
    tableName = "price_history",
    foreignKeys = [
        ForeignKey(
            entity = AssetRecord::class,
            parentColumns = ["id"],
            childColumns = ["recordId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["recordId"]), Index(value = ["timestamp"])]
)
data class PriceHistory(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val recordId: String,                    // 关联的 AssetRecord ID
    val price: Double,                      // 当日价格/净值
    val cost: Double,                       // 当日成本（用于计算收益）
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * 收益 = 当前价 - 成本
     */
    val profitLoss: Double
        get() = price - cost

    /**
     * 收益率
     */
    val profitLossRatio: Double
        get() = if (cost > 0) profitLoss / cost else 0.0
}