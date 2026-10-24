package com.finunity.data.model

import com.finunity.data.local.entity.FinancialGoal
import com.finunity.data.local.entity.PlanningProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialPlannerEngineTest {
    @Test
    fun calculatesMonthlyBudgetReserveAndGoalProjection() {
        val result = FinancialPlannerEngine.calculate(snapshot())

        assertTrue(result.canConfirm)
        assertEquals("600.00", result.emergencyReserve)
        assertEquals("4400.00", result.cashAvailableForGoals)
        assertEquals("500.00", result.monthlySurplus)
        assertEquals("500.00", result.monthlyGoalBudget)
        assertEquals("916.67", result.goals.single().requiredMonthly)
        assertEquals("7000.00", result.goals.single().projectedAmount)
        assertEquals("5000.00", result.goals.single().projectedShortfall)
    }

    @Test
    fun missingInputsAndUnknownCashFxCannotBeConfirmed() {
        val incomplete = snapshot().copy(
            profile = PlanningProfile(
                monthlyIncome = null,
                monthlyExpenses = "300",
                essentialExpenses = null
            ),
            availableCashKnown = false
        )

        val result = FinancialPlannerEngine.calculate(incomplete)

        assertFalse(result.canConfirm)
        assertTrue(result.missing.any { it.contains("月收入") })
        assertTrue(result.missing.any { it.contains("必要支出") })
        assertTrue(result.missing.any { it.contains("汇率") })
    }

    @Test
    fun allocatedGoalsCannotExceedCashAfterEmergencyReserve() {
        val overAllocated = snapshot().copy(
            availableCash = "1000",
            goals = listOf(snapshot().goals.single().copy(allocatedAmount = "500"))
        )

        val result = FinancialPlannerEngine.calculate(overAllocated)

        assertFalse(result.canConfirm)
        assertTrue(result.errors.any { it.contains("超过") })
    }

    @Test
    fun inputFingerprintChangesWhenGoalChanges() {
        val original = snapshot()
        val changed = original.copy(goals = listOf(original.goals.single().copy(targetAmount = "13000")))

        assertFalse(
            FinancialPlannerEngine.fingerprint(original) == FinancialPlannerEngine.fingerprint(changed)
        )
    }

    private fun snapshot() = FinancialPlanSnapshot(
        profile = PlanningProfile(
            monthlyIncome = "1000",
            monthlyExpenses = "400",
            monthlyDebtPayment = "100",
            essentialExpenses = "200",
            reserveMonths = 3
        ),
        goals = listOf(
            FinancialGoal(
                id = "goal-1",
                name = "应急储备",
                targetAmount = "12000",
                allocatedAmount = "1000",
                monthlyContribution = "500",
                targetDate = "2027-01-01"
            )
        ),
        availableCash = "5000",
        baseCurrency = "CNY",
        asOfDate = "2026-01-01"
    )
}
