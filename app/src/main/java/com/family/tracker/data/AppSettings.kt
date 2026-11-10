package com.family.tracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Int = 1,
    val latitude: Double = 21.4225,
    val longitude: Double = 39.8262,
    val cityName: String = "Makkah",
    val methodId: String = "MUSLIM_WORLD_LEAGUE",
    val homeworkReminder: Boolean = false,
    val prayerReminder: Boolean = false,
    val isChildDevice: Boolean = false,
    val enabledAdhans: String = "Fajr,Dhuhr,Asr,Maghrib,Isha"
)