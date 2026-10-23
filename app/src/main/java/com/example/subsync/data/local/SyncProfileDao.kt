package com.example.subsync.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncProfileDao {
    @Query("SELECT * FROM sync_profiles ORDER BY lastUsedTimestamp DESC")
    fun getAllProfilesFlow(): Flow<List<SyncProfileEntity>>

    @Query("SELECT * FROM sync_profiles WHERE openSubtitlesHash = :hash LIMIT 1")
    suspend fun findByOpenSubtitlesHash(hash: String): SyncProfileEntity?

    @Query("SELECT * FROM sync_profiles WHERE sha256Hash = :sha256 LIMIT 1")
    suspend fun findBySha256(sha256: String): SyncProfileEntity?

    @Query("SELECT * FROM sync_profiles WHERE mediaTitle LIKE '%' || :query || '%' OR subtitleTitle LIKE '%' || :query || '%'")
    suspend fun searchProfiles(query: String): List<SyncProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: SyncProfileEntity): Long

    @Update
    suspend fun updateProfile(profile: SyncProfileEntity)

    @Delete
    suspend fun deleteProfile(profile: SyncProfileEntity)

    @Query("DELETE FROM sync_profiles WHERE id = :id")
    suspend fun deleteById(id: Long)
}
