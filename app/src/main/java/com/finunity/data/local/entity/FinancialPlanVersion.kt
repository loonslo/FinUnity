package com.finunity.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** Immutable input/result snapshot for a draft or confirmed goal plan. */
@Entity(
    tableName = "financial_plan_versions",
    indices = [Index("status"), Index("createdAt")]
)
data class FinancialPlanVersion(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val status: String = STATUS_DRAFT,
    val inputFingerprint: String,
    val snapshotJson: String,
    val resultJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    val confirmedAt: Long? = null
) {
    companion object {
        const val STATUS_DRAFT = "draft"
        const val STATUS_CONFIRMED = "confirmed"
        const val STATUS_SUPERSEDED = "superseded"
    }
}
