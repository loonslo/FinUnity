package com.finunity.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.finunity.data.local.entity.FinancialGoal
import com.finunity.data.local.entity.FinancialPlanReview
import com.finunity.data.local.entity.FinancialPlanVersion
import com.finunity.data.local.entity.PlanningProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface FinancialPlannerDao {
    @Query("SELECT * FROM planning_profile WHERE id = 1")
    fun observeProfile(): Flow<PlanningProfile?>

    @Query("SELECT * FROM planning_profile WHERE id = 1")
    suspend fun getProfile(): PlanningProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: PlanningProfile)

    @Query("SELECT * FROM financial_goals ORDER BY priority, createdAt")
    fun observeGoals(): Flow<List<FinancialGoal>>

    @Query("SELECT * FROM financial_goals ORDER BY priority, createdAt")
    suspend fun getGoals(): List<FinancialGoal>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveGoal(goal: FinancialGoal)

    @Delete
    suspend fun deleteGoal(goal: FinancialGoal)

    @Query("SELECT * FROM financial_plan_versions ORDER BY createdAt DESC")
    fun observePlans(): Flow<List<FinancialPlanVersion>>

    @Query("SELECT * FROM financial_plan_versions WHERE id = :id")
    suspend fun getPlan(id: String): FinancialPlanVersion?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePlan(plan: FinancialPlanVersion)

    @Query("UPDATE financial_plan_versions SET status = :status WHERE id = :id")
    suspend fun updatePlanStatus(id: String, status: String)

    @Query("UPDATE financial_plan_versions SET status = 'superseded' WHERE status = 'confirmed'")
    suspend fun supersedeConfirmedPlans()

    @Query("SELECT * FROM financial_plan_reviews ORDER BY month DESC, createdAt DESC")
    fun observeReviews(): Flow<List<FinancialPlanReview>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveReview(review: FinancialPlanReview)

    @Query("DELETE FROM planning_profile")
    suspend fun deleteProfiles()

    @Query("DELETE FROM financial_goals")
    suspend fun deleteGoals()

    @Query("DELETE FROM financial_plan_versions")
    suspend fun deletePlans()

    @Query("DELETE FROM financial_plan_reviews")
    suspend fun deleteReviews()
}
