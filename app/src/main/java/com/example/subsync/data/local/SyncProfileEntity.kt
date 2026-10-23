package com.example.subsync.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_profiles")
data class SyncProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mediaTitle: String,
    val openSubtitlesHash: String,
    val sha256Hash: String = "",
    val subtitleTitle: String,
    val calibratedOffsetMs: Long,
    val driftFactor: Double = 1.0,
    val confidencePercent: Int,
    val syncMethod: String,
    val cueCount: Int,
    val mediaDurationMs: Long,
    val lastUsedTimestamp: Long = System.currentTimeMillis()
)
