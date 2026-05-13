package com.finunity.data.local.dao

import androidx.room.*
import com.finunity.data.local.entity.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY timestamp DESC")
    fun getTransactionsByAccount(accountId: String): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE symbol = :symbol ORDER BY timestamp DESC")
    fun getTransactionsBySymbol(symbol: String): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE recordId = :recordId ORDER BY timestamp DESC")
    fun getTransactionsByRecordId(recordId: String): Flow<List<Transaction>>

    /**
     * 计算交易流水推导余额。
     * 当前只作为审计核对，不参与总资产统计。
     * 按时间顺序累加，返回最新余额
     */
    @Query("""
        SELECT COALESCE(
            (SELECT balanceAfter FROM transactions
             WHERE accountId = :accountId
             ORDER BY timestamp DESC, id DESC
             LIMIT 1),
            0.0
        )
    """)
    suspend fun getComputedBalance(accountId: String): Double

    /**
     * 获取账户所有交易按时间排序（用于余额重算）
     */
    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY timestamp ASC, id ASC")
    suspend fun getTransactionsForReconciliation(accountId: String): List<Transaction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: Transaction)

    @Delete
    suspend fun delete(transaction: Transaction)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: String)
}
