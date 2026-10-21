package com.ecotracker.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ActivityDao {

    @Insert
    suspend fun insertActivity(activity: ActivityEntity)

    @Query("SELECT * FROM activities WHERE firebaseUid = :uid ORDER BY timestamp DESC")
    suspend fun getActivities(uid: String): List<ActivityEntity>

    @Query("DELETE FROM activities WHERE firebaseUid = :uid")
    suspend fun deleteActivities(uid: String)
}