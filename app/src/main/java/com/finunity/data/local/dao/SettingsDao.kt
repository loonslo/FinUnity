package com.finunity.data.local.dao

import androidx.room.*
import com.finunity.data.local.entity.Settings
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {

    @Query("SELECT * FROM settings WHERE id = 1")
    fun getSettings(): Flow<Settings?>

    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun getSettingsOnce(): Settings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(settings: Settings)

    @Update
    suspend fun update(settings: Settings)
}
