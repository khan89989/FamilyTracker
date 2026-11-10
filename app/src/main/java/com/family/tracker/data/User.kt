package com.family.tracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val passwordHash: String,
    val role: String = "CHILD",
    val profileImageUri: String? = null,
    val backupPassword: String? = null,
    val points: Double = 0.0 // CHANGED TO DOUBLE
)