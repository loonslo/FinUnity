package com.finunity.data.local.dao

import androidx.room.*
import com.finunity.data.local.entity.AssetSnapshot
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetSnapshotDao {

    @Query("SELECT * FROM asset_snapshots ORDER BY timestamp DESC")
    fun getAllSnapshots(): Flow<List<AssetSnapshot>>

    @Query("SELECT * FROM asset_snapshots WHERE timestamp >= :startTime ORDER BY timestamp ASC")
    fun getSnapshotsSince(startTime: Long): Flow<List<AssetSnapshot>>

    @Query("SELECT * FROM asset_snapshots ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestSnapshot(): AssetSnapshot?

    @Query("SELECT * FROM asset_snapshots ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentSnapshots(limit: Int): Flow<List<AssetSnapshot>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: AssetSnapshot)

    @Delete
    suspend fun delete(snapshot: AssetSnapshot)

    @Query("DELETE FROM asset_snapshots WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("SELECT COUNT(*) FROM asset_snapshots")
    suspend fun getSnapshotCount(): Int
}