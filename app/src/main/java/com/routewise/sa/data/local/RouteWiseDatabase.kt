package com.routewise.sa.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [UserEntity::class, IncidentEntity::class, NotificationLogEntity::class], version = 2, exportSchema = false)
abstract class RouteWiseDatabase : RoomDatabase() {
    abstract fun dao(): RouteWiseDao

    companion object {
        @Volatile
        private var INSTANCE: RouteWiseDatabase? = null

        fun getDatabase(context: Context): RouteWiseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RouteWiseDatabase::class.java,
                    "routewise_database"
                )
                .fallbackToDestructiveMigration() // For prototype simplicity
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}