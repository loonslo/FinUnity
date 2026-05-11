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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: Transaction)

    @Delete
    suspend fun delete(transaction: Transaction)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: String)
}
