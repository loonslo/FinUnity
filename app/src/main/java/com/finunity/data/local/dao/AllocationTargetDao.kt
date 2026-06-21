package com.finunity.data.local.dao

import androidx.room.*
import com.finunity.data.local.entity.AllocationTarget
import kotlinx.coroutines.flow.Flow

/**
 * 落点目标 DAO
 */
@Dao
interface AllocationTargetDao {

    @Query("SELECT * FROM allocation_targets ORDER BY riskBucket, subCategory")
    fun getAllTargets(): Flow<List<AllocationTarget>>

    @Query("SELECT * FROM allocation_targets WHERE subCategory = :subCategory")
    suspend fun getTarget(subCategory: String): AllocationTarget?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(target: AllocationTarget)

    @Query("DELETE FROM allocation_targets WHERE subCategory = :subCategory")
    suspend fun deleteBySubCategory(subCategory: String)

    @Query("DELETE FROM allocation_targets")
    suspend fun deleteAll()
}
