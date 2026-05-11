package com.finunity.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * 持仓实体
 * 使用平均成本法计算
 */
@Entity(
    tableName = "positions",
    foreignKeys = [
        ForeignKey(
            entity = Account::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["accountId"])]
)
data class Position(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val accountId: String,              // 关联账户ID
    val symbol: String,                // 股票代码，如 "AAPL", "00700.HK"
    val shares: Double,                // 股数
    val totalCost: Double,              // 总成本（买入总金额）
    val currency: String = "USD",      // 明确币种
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * 平均成本 = 总成本 / 股数
     */
    val averageCost: Double
        get() = if (shares > 0) totalCost / shares else 0.0
}
