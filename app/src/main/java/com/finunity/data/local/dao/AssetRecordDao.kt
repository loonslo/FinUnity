package com.finunity.data.local.dao

import androidx.room.*
import com.finunity.data.local.entity.AssetRecord
import com.finunity.data.local.entity.AssetType
import kotlinx.coroutines.flow.Flow

/**
 * 资产记录 DAO
 */
@Dao
interface AssetRecordDao {

    @Query("SELECT * FROM asset_records")
    fun getAllRecords(): Flow<List<AssetRecord>>

    @Query("SELECT * FROM asset_records WHERE accountId = :accountId")
    fun getRecordsByAccount(accountId: String): Flow<List<AssetRecord>>

    @Query("SELECT * FROM asset_records WHERE assetType = :assetType")
    fun getRecordsByType(assetType: AssetType): Flow<List<AssetRecord>>

    @Query("SELECT * FROM asset_records WHERE id = :id")
    suspend fun getRecordById(id: String): AssetRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: AssetRecord)

    @Update
    suspend fun update(record: AssetRecord)

    @Delete
    suspend fun delete(record: AssetRecord)

    @Query("DELETE FROM asset_records WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM asset_records WHERE accountId = :accountId")
    suspend fun deleteByAccountId(accountId: String)

    @Query("SELECT SUM(quantity * currentPrice) FROM asset_records WHERE currency = :currency")
    suspend fun getTotalValueByCurrency(currency: String): Double?

    @Query("SELECT SUM(quantity * currentPrice) FROM asset_records")
    suspend fun getTotalValue(): Double?

    @Query("SELECT * FROM asset_records WHERE assetType IN (:types)")
    suspend fun getRecordsByTypes(types: List<String>): List<AssetRecord>
}
