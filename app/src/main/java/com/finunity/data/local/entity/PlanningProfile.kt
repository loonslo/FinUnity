package com.finunity.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** User-confirmed monthly assumptions used by the goal planner, in the app base currency. */
@Entity(tableName = "planning_profile")
data class PlanningProfile(
    @PrimaryKey val id: Int = 1,
    val monthlyIncome: String? = null,
    val monthlyExpenses: String? = null,
    val monthlyDebtPayment: String? = null,
    val essentialExpenses: String? = null,
    val reserveMonths: Int = 6,
    val updatedAt: Long = System.currentTimeMillis()
)
