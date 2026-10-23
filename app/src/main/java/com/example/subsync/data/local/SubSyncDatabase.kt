package com.example.subsync.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SyncProfileEntity::class], version = 1, exportSchema = false)
abstract class SubSyncDatabase : RoomDatabase() {
    abstract fun syncProfileDao(): SyncProfileDao

    companion object {
        @Volatile
        private var INSTANCE: SubSyncDatabase? = null

        fun getDatabase(context: Context): SubSyncDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SubSyncDatabase::class.java,
                    "subsync_database"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
