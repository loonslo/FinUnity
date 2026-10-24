package com.finunity.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** Saved monthly review snapshot. Re-saving a plan/month replaces only that review snapshot. */
@Entity(
    tableName = "financial_plan_reviews",
    primaryKeys = ["planId", "month"],
    foreignKeys = [
        ForeignKey(
            entity = FinancialPlanVersion::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("month")]
)
data class FinancialPlanReview(
    val planId: String,
    /** ISO-8601 year-month, for example 2026-10. */
    val month: String,
    val actualIncome: String,
    val actualExpenses: String,
    val actualGoalContributionsJson: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
