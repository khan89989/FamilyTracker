package com.family.tracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val childId: Long,
    val title: String,
    val prayerReference: String = "None",
    val offsetMinutes: Int = 0,
    val repeatType: String = "DAILY",
    val repeatDays: String = "",
    val startDate: String = "",       // Format: yyyy-MM-dd
    val optionalEndDate: String = ""  // Format: yyyy-MM-dd (empty if none)
)