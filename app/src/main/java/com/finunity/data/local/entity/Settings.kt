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
    // 三桶目标配置，键顺序固定，便于备份、展示和迁移审计。
    val targetAllocation: String = DEFAULT_TARGET_ALLOCATION,
    val rebalanceThreshold: Double = 0.05,  // 再平衡阈值，默认5%偏离度触发提醒
    val onboarded: Boolean = false,          // 是否已完成新手引导
    val amountsVisible: Boolean = true,      // 金额是否可见（全局隐藏开关）
    // 永不满仓 · 风险仓位上限：进取（生钱的钱）占比超过此值即提示"风险仓位偏高"。默认 0.70，0/1 之外视为不启用
    val maxAggressiveRatio: Double = 0.70,
    // 配色方案："minimal"(极简灰蓝) / "navy"(藏青)，对应 Web 端 data-theme-color
    val themeColor: String = "navy",
    // 外观模式："system" / "light" / "dark"，对应 Web 端 data-appearance
    val themeAppearance: String = "dark"
)

/**
 * 解析 targetAllocation 字符串
 * @return 资产类别到目标比例的映射
 */
const val DEFAULT_TARGET_ALLOCATION = "DEFENSIVE:0.1,BALANCED:0.6,AGGRESSIVE:0.3"

data class TargetAllocationValidation(
    val values: Map<String, Double>,
    val errors: List<String>
) {
    val isValid: Boolean get() = errors.isEmpty()
}

private val THREE_BUCKET_KEYS = listOf("DEFENSIVE", "BALANCED", "AGGRESSIVE")

/**
 * 解析并规范化目标配置。旧 CONSERVATIVE + INSURANCE 会合并为 BALANCED，旧 CASH 会变为
 * DEFENSIVE。解析函数保留合法片段，完整合法性由 [validateTargetAllocation] 判定。
 */
fun parseTargetAllocation(allocationStr: String): Map<String, Double> =
    parseTargetAllocationInternal(allocationStr).values

fun validateTargetAllocation(allocationStr: String): TargetAllocationValidation {
    val parsed = parseTargetAllocationInternal(allocationStr)
    val errors = parsed.errors.toMutableList()
    THREE_BUCKET_KEYS.filterNot(parsed.values::containsKey).forEach { key ->
        errors += "缺少目标桶 $key"
    }
    if (parsed.values.isNotEmpty()) {
        val sum = parsed.values.values.sum()
        if (kotlin.math.abs(sum - 1.0) > 1e-6) {
            errors += "目标比例合计必须为 1，当前为 $sum"
        }
    }
    return TargetAllocationValidation(parsed.values, errors.distinct())
}

fun parseTargetAllocationOrDefault(allocationStr: String): Map<String, Double> =
    validateTargetAllocation(allocationStr).takeIf { it.isValid }?.values
        ?: parseTargetAllocation(DEFAULT_TARGET_ALLOCATION)

fun formatTargetAllocation(targets: Map<String, Double>): String {
    val validation = validateTargetAllocation(
        THREE_BUCKET_KEYS.joinToString(",") { "$it:${targets[it] ?: 0.0}" }
    )
    require(validation.isValid) { validation.errors.joinToString("；") }
    return THREE_BUCKET_KEYS.joinToString(",") { "$it:${targets.getValue(it)}" }
}

private data class ParsedTargetAllocation(
    val values: Map<String, Double>,
    val errors: List<String>
)

private fun parseTargetAllocationInternal(allocationStr: String): ParsedTargetAllocation {
    if (allocationStr.isBlank()) return ParsedTargetAllocation(emptyMap(), emptyList())
    val values = linkedMapOf<String, Double>()
    val errors = mutableListOf<String>()
    val rawKeys = mutableSetOf<String>()

    allocationStr.split(",").forEachIndexed { index, pair ->
        val parts = pair.split(":", limit = 2)
        if (parts.size != 2 || parts[0].trim().isEmpty()) {
            errors += "第 ${index + 1} 项格式应为 KEY:比例"
            return@forEachIndexed
        }
        val rawKey = parts[0].trim()
        val key = legacyBucketToThreeBucket(rawKey)?.name
        if (key == null) {
            errors += "未知目标桶 '$rawKey'"
            return@forEachIndexed
        }
        val rawKeyUpper = rawKey.uppercase()
        // CONSERVATIVE 和 INSURANCE 是唯一允许合并到同一新键的两个旧键；其余重复项均非法。
        val isDistinctLegacyPair =
            (rawKeyUpper == "CONSERVATIVE" && rawKeys.contains("INSURANCE")) ||
                (rawKeyUpper == "INSURANCE" && rawKeys.contains("CONSERVATIVE"))
        if (key in values && !isDistinctLegacyPair) {
            errors += "目标桶 '$key' 重复"
            return@forEachIndexed
        }
        val value = parts[1].trim().toDoubleOrNull()
        if (value == null || !value.isFinite()) {
            errors += "目标桶 '$rawKey' 的比例不是有限数字"
            return@forEachIndexed
        }
        if (value < 0.0) {
            errors += "目标桶 '$rawKey' 的比例不能为负数"
            return@forEachIndexed
        }
        values[key] = (values[key] ?: 0.0) + value
        rawKeys += rawKeyUpper
    }
    return ParsedTargetAllocation(values, errors)
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
    val normalizedCurrent = currentAllocation.entries.groupBy { legacyBucketToThreeBucket(it.key)?.name ?: it.key }
        .mapValues { (_, entries) -> entries.sumOf { it.value } }
    val normalizedTarget = targetAllocation.entries.groupBy { legacyBucketToThreeBucket(it.key)?.name ?: it.key }
        .mapValues { (_, entries) -> entries.sumOf { it.value } }
    fun labelOf(asset: String): String = when (asset) {
        "DEFENSIVE" -> "防守"
        "BALANCED" -> "稳健"
        "AGGRESSIVE" -> "进攻"
        else -> asset
    }

    val recommendations = mutableListOf<String>()
    for ((asset, target) in normalizedTarget) {
        val current = normalizedCurrent[asset] ?: 0.0
        val drift = current - target
        if (kotlin.math.abs(drift) > threshold) {
            val action = if (drift > 0) "减配" else "增配"
            recommendations.add("${labelOf(asset)}: 当前${(current * 100).toInt()}%, 目标${(target * 100).toInt()}%, 建议$action${(kotlin.math.abs(drift) * 100).toInt()}%")
        }
    }
    return recommendations
}
