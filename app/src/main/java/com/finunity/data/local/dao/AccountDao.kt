package com.finunity.data.local.dao

import androidx.room.*
import com.finunity.data.local.entity.Account
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts ORDER BY createdAt DESC")
    fun getAllAccounts(): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getAccountById(id: String): Account?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: Account)

    @Update
    suspend fun update(account: Account)

    @Delete
    suspend fun delete(account: Account)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: String)
}
