package com.finunity.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 落点目标（子桶级目标配置）
 *
 * 对应方案第三章"加仓落点表"：四象限之下、每个具体落点（如标普500、纳指100、红利、训练仓、弹药）
 * 各自的目标金额、上限红线与停止条件。资产通过 [AssetRecord.subCategory] 归入对应落点，
 * 由 PortfolioCalculator 汇总出"目标/现有/缺口/是否触顶"的落点表。
 *
 * 金额一律以基准货币（默认 CNY）计。
 */
@Entity(tableName = "allocation_targets")
data class AllocationTarget(
    @PrimaryKey
    val subCategory: String,                  // 落点名称（与 AssetRecord.subCategory 对应），唯一
    val riskBucket: RiskBucket,               // 所属象限（稳健/进取/防守/保命）
    val targetAmount: Double,                 // 目标金额（基准货币）
    val capAmount: Double = 0.0,              // 上限/红线金额，0 表示不设上限
    val stopNote: String = "",                // 停止条件文字，如"投满21.5万即停""达9万后只再平衡"
    val updatedAt: Long = System.currentTimeMillis()
)
