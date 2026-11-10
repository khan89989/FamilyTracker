package com.family.tracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    @Update
    suspend fun updateUser(user: User)

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getUserById(id: Long): User?

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): User?

    @Query("SELECT * FROM users")
    suspend fun getAllUsers(): List<User>

    @Query("SELECT * FROM users WHERE role = 'CHILD'")
    suspend fun getAllChildren(): List<User>

    @Query("UPDATE users SET points = points + :points WHERE id = :userId")
    suspend fun updatePoints(userId: Long, points: Double) // CHANGED TO DOUBLE

    @Query("DELETE FROM users WHERE role = 'CHILD'")
    suspend fun deleteAllChildren()

    @Query("DELETE FROM users WHERE role = 'PARENT'")
    suspend fun deleteAllParents()
}