package com.finunity.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * 资产历史快照
 * 定期记录总资产状态，用于展示历史曲线和分析
 */
@Entity(tableName = "asset_snapshots")
data class AssetSnapshot(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val totalAssets: Double,           // 总资产
    val cashAssets: Double,           // 现金资产
    val stockAssets: Double,          // 股票资产
    val stockRatio: Double,           // 股票占比
    val baseCurrency: String,         // 基准货币
    val totalCost: Double,            // 总成本（用于计算累计收益）
    val notes: String? = null         // 备注，如"月末快照"
)

/**
 * 快照类型
 */
enum class SnapshotType {
    DAILY,     // 每日快照
    WEEKLY,    // 每周快照
    MONTHLY,   // 每月快照
    MANUAL     // 手动触发
}