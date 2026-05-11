package com.finunity.data.local.dao

import androidx.room.*
import com.finunity.data.local.entity.Position
import kotlinx.coroutines.flow.Flow

@Dao
interface PositionDao {

    @Query("SELECT * FROM positions ORDER BY symbol ASC")
    fun getAllPositions(): Flow<List<Position>>

    @Query("SELECT * FROM positions WHERE accountId = :accountId ORDER BY symbol ASC")
    fun getPositionsByAccount(accountId: String): Flow<List<Position>>

    @Query("SELECT * FROM positions WHERE symbol = :symbol")
    suspend fun getPositionsBySymbol(symbol: String): List<Position>

    @Query("SELECT * FROM positions WHERE id = :id")
    suspend fun getPositionById(id: String): Position?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(position: Position)

    @Update
    suspend fun update(position: Position)

    @Delete
    suspend fun delete(position: Position)

    @Query("DELETE FROM positions WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * 获取所有不重复的股票代码
     */
    @Query("SELECT DISTINCT symbol FROM positions")
    suspend fun getAllSymbols(): List<String>
}
