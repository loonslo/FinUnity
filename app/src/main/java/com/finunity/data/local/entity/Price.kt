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
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * 价格是否过期（超过10分钟）
     */
    fun isStale(): Boolean {
        return System.currentTimeMillis() - updatedAt > 10 * 60 * 1000
    }
}
