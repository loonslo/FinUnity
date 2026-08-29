package com.finunity.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** A local recurring income/expense rule. It never connects to a bank or broker. */
@Entity(
    tableName = "recurring_rules",
    foreignKeys = [
        ForeignKey(
            entity = Account::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["accountId"])]
)
data class RecurringRule(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val accountId: String,
    val type: RecurringRuleType,
    val amount: Double,
    val currency: String,
    val category: CashFlowCategory,
    val note: String,
    val dayOfMonth: Int = 1,
    val enabled: Boolean = true,
    val lastGeneratedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

enum class RecurringRuleType {
    INCOME,
    EXPENSE
}
