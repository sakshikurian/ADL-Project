package com.ecotracker.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(

    @PrimaryKey
    val firebaseUid: String,

    val name: String,
    val email: String,
    val joinDate: String,

    val ecoScore: Int,
    val ecoScoreMax: Int,
    val scoreDeltaThisWeek: Int,
    val weeklyActivities: Int,

    val co2SavedKg: Double,
    val waterSavedLitres: Int,
    val streakDays: Int,

    val challengeJoined: Boolean,

    val progressCompleted: Int,
    val progressTotal: Int,

    val notificationsEnabled: Boolean,
    val reminderHour: Int,
    val reminderMinute: Int
)