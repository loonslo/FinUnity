package com.finunity.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** A household goal. Goal amounts are decimal strings in the selected base currency. */
@Entity(tableName = "financial_goals", indices = [Index("priority")])
data class FinancialGoal(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val targetAmount: String,
    val allocatedAmount: String = "0",
    val monthlyContribution: String = "0",
    /** ISO-8601 calendar date, independent of device timezone. */
    val targetDate: String,
    val priority: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
