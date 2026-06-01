package com.finunity.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 设置实体
 * 存储用户配置
 */
@Entity(tableName = "settings")
data class Settings(
    @PrimaryKey
    val id: Int = 1,                    // 始终为1，单例
    val baseCurrency: String = "CNY",   // 基准货币
    // 目标资产配置（标普四象限），key：CONSERVATIVE/AGGRESSIVE/INSURANCE/CASH（稳健/进取/保命/防守）
    // 默认采用经典标普比例：保本40% 生钱30% 保命20% 要花10%
    val targetAllocation: String = "CONSERVATIVE:0.4,AGGRESSIVE:0.3,INSURANCE:0.2,CASH:0.1",
    val rebalanceThreshold: Double = 0.05,  // 再平衡阈值，默认5%偏离度触发提醒
    val onboarded: Boolean = false,          // 是否已完成新手引导
    val amountsVisible: Boolean = true       // 金额是否可见（全局隐藏开关）
)

/**
 * 解析 targetAllocation 字符串
 * @return 资产类别到目标比例的映射
 */
fun parseTargetAllocation(allocationStr: String): Map<String, Double> {
    fun normalizeKey(key: String): String = when (key.trim().uppercase()) {
        "稳健", "CONSERVATIVE" -> "CONSERVATIVE"
        "进取", "AGGRESSIVE" -> "AGGRESSIVE"
        "保命", "INSURANCE" -> "INSURANCE"
        "防守", "CASH" -> "CASH"
        else -> key.trim()
    }

    return allocationStr.split(",")
        .mapNotNull { pair ->
            val parts = pair.split(":")
            if (parts.size == 2) {
                val key = normalizeKey(parts[0])
                val value = parts[1].trim().toDoubleOrNull()
                if (value != null) key to value else null
            } else null
        }
        .toMap()
}

/**
 * 计算再平衡建议
 * @param currentAllocation 当前资产配置
 * @param targetAllocation 目标资产配置
 * @param threshold 偏离阈值
 * @return 需要调仓的建议列表
 */
fun calculateRebalanceRecommendations(
    currentAllocation: Map<String, Double>,
    targetAllocation: Map<String, Double>,
    threshold: Double
): List<String> {
    fun labelOf(asset: String): String = when (asset) {
        "CONSERVATIVE" -> "稳健"
        "AGGRESSIVE" -> "进取"
        "INSURANCE" -> "保命"
        "CASH" -> "防守"
        else -> asset
    }

    val recommendations = mutableListOf<String>()
    for ((asset, target) in targetAllocation) {
        val current = currentAllocation[asset] ?: 0.0
        val drift = current - target
        if (kotlin.math.abs(drift) > threshold) {
            val action = if (drift > 0) "减配" else "增配"
            recommendations.add("${labelOf(asset)}: 当前${(current * 100).toInt()}%, 目标${(target * 100).toInt()}%, 建议$action${(kotlin.math.abs(drift) * 100).toInt()}%")
        }
    }
    return recommendations
}
