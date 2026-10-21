package com.ecotracker.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "activities")
data class ActivityEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val firebaseUid: String,

    val title: String,

    val points: Int,

    val co2SavedKg: Double,

    val waterSavedLitres: Int,

    val timestamp: Long = System.currentTimeMillis()
)