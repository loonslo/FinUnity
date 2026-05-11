package com.finunity.data.local.dao

import androidx.room.*
import com.finunity.data.local.entity.PriceHistory
import kotlinx.coroutines.flow.Flow

/**
 * 价格历史 DAO
 */
@Dao
interface PriceHistoryDao {

    @Query("SELECT * FROM price_history WHERE recordId = :recordId ORDER BY timestamp DESC")
    fun getHistoryByRecord(recordId: String): Flow<List<PriceHistory>>

    @Query("SELECT * FROM price_history WHERE recordId = :recordId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestPrice(recordId: String): PriceHistory?

    @Query("SELECT * FROM price_history WHERE recordId = :recordId ORDER BY timestamp ASC")
    suspend fun getAllHistoryAscending(recordId: String): List<PriceHistory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(priceHistory: PriceHistory)

    @Delete
    suspend fun delete(priceHistory: PriceHistory)

    @Query("DELETE FROM price_history WHERE recordId = :recordId")
    suspend fun deleteByRecordId(recordId: String)

    @Query("SELECT COUNT(*) FROM price_history WHERE recordId = :recordId")
    suspend fun getHistoryCount(recordId: String): Int
}