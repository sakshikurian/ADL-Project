package com.ecotracker.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserEntity::class,
        ActivityEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class EcoDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun activityDao(): ActivityDao

    companion object {

        @Volatile
        private var INSTANCE: EcoDatabase? = null

        fun getDatabase(context: Context): EcoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    EcoDatabase::class.java,
                    "eco_tracker_database"
                ).build()

                INSTANCE = instance
                instance
            }
        }
    }
}