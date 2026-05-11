package com.finunity.data.local.dao

import androidx.room.*
import com.finunity.data.local.entity.Price

@Dao
interface PriceDao {

    @Query("SELECT * FROM prices WHERE symbol = :symbol")
    suspend fun getPrice(symbol: String): Price?

    @Query("SELECT * FROM prices")
    suspend fun getAllPrices(): List<Price>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(price: Price)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(prices: List<Price>)

    @Query("DELETE FROM prices WHERE symbol = :symbol")
    suspend fun delete(symbol: String)

    @Query("DELETE FROM prices")
    suspend fun deleteAll()
}
