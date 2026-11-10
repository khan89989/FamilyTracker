package com.family.tracker.data

data class BackupUser(
    val id: Long = 0,
    val username: String = "",
    val passwordHash: String = "",
    val role: String = "CHILD",
    val profileImageBase64: String? = null,
    val points: Int = 0
)

data class BackupData(
    val schemaVersion: Int = 1,
    val generatedAt: Long = System.currentTimeMillis(),
    val settings: AppSettings? = null, // NEW
    val users: List<BackupUser> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val habits: List<Habit> = emptyList(),
    val habitLogs: List<HabitLog> = emptyList(),
    val subjects: List<Subject> = emptyList(),
    val homework: List<Homework> = emptyList(),
    val prayerLogs: List<PrayerLog> = emptyList()
)