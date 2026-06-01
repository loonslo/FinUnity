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
     * 价格是否过期。
     * 本应用按"每日更新一次"设计：每日 Worker 负责刷新，按需读取优先走缓存。
     * 因此过期阈值放宽到 12 小时，避免每次组合重算都打网络。
     */
    fun isStale(): Boolean {
        return System.currentTimeMillis() - updatedAt > STALE_THRESHOLD_MS
    }

    /**
     * 价格的置信度描述（按"天"为尺度）
     */
    fun confidence(): PriceConfidence {
        val age = System.currentTimeMillis() - updatedAt
        return when {
            age <= 12 * 60 * 60 * 1000 -> PriceConfidence.REAL_TIME      // 当日数据
            age <= 24 * 60 * 60 * 1000 -> PriceConfidence.NEAR_REALTIME  // 一天内
            age <= 3L * 24 * 60 * 60 * 1000 -> PriceConfidence.DELAYED   // 三天内
            else -> PriceConfidence.STALE                                 // 超过三天
        }
    }

    companion object {
        /** 价格过期阈值：12 小时 */
        const val STALE_THRESHOLD_MS = 12L * 60 * 60 * 1000
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
