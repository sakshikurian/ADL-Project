package com.ecotracker.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UserDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Query("SELECT * FROM users WHERE firebaseUid = :uid LIMIT 1")
    suspend fun getUser(uid: String): UserEntity?

    @Query("DELETE FROM users WHERE firebaseUid = :uid")
    suspend fun deleteUser(uid: String)
}