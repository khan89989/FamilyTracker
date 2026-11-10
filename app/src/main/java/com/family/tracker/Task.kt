package com.family.tracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val childId: Long,
    val title: String,
    val assignedDate: String,
    val description: String = "",
    val status: String = "PENDING",
    val startTimeMillis: Long = 0L,
    val endTimeMillis: Long = 0L,
    val completionTimeMillis: Long = 0L,
    val hasAlarm: Boolean = false
)