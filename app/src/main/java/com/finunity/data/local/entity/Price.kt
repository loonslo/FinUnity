package com.finunity.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 价格缓存实体
 * 存储从 Yahoo Finance 获取的股价和汇率
 */
@Entity(tableName = "prices")
data class Price(
    @PrimaryKey
    val symbol: String,                // 股票代码或货币对，如 "AAPL", "USD/CNY"
    val price: Double,                // 当前价格
    val currency: String,              // 价格货币
    val updatedAt: Long = System.currentTimeMillis(),
    val isFallback: Boolean = false     // 是否是过期缓存回退（而非实时数据）
) {
    /**
     * 价格是否过期（超过10分钟）
     */
    fun isStale(): Boolean {
        return System.currentTimeMillis() - updatedAt > 10 * 60 * 1000
    }

    /**
     * 价格的置信度描述
     */
    fun confidence(): PriceConfidence {
        val age = System.currentTimeMillis() - updatedAt
        return when {
            age <= 5 * 60 * 1000 -> PriceConfidence.REAL_TIME
            age <= 30 * 60 * 1000 -> PriceConfidence.NEAR_REALTIME
            age <= 2 * 60 * 60 * 1000 -> PriceConfidence.DELAYED
            else -> PriceConfidence.STALE
        }
    }
}

/**
 * 价格置信度
 */
enum class PriceConfidence {
    REAL_TIME,     // 5分钟内，数据新鲜
    NEAR_REALTIME, // 30分钟内，数据较新
    DELAYED,       // 2小时内，有延迟但可用
    STALE          // 超过2小时，仅展示不用于决策
}
