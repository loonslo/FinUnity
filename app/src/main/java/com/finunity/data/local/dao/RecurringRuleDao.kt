package com.finunity.data.local.dao

import androidx.room.*
import com.finunity.data.local.entity.RecurringRule
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringRuleDao {
    @Query("SELECT * FROM recurring_rules ORDER BY enabled DESC, createdAt DESC")
    fun getAll(): Flow<List<RecurringRule>>

    @Query("SELECT * FROM recurring_rules WHERE enabled = 1")
    suspend fun getEnabled(): List<RecurringRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: RecurringRule)

    @Update
    suspend fun update(rule: RecurringRule)

    @Delete
    suspend fun delete(rule: RecurringRule)

    @Query("DELETE FROM recurring_rules")
    suspend fun deleteAll()
}
