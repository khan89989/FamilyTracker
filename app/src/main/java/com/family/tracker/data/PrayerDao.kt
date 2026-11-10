package com.family.tracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PrayerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateLog(log: PrayerLog)

    @Query("SELECT * FROM prayer_logs WHERE childId = :childId AND date = :date")
    suspend fun getLogsForChildByDate(childId: Long, date: String): List<PrayerLog>

    @Query("SELECT * FROM prayer_logs WHERE childId = :childId AND date = :date")
    suspend fun getLogsForDate(childId: Long, date: String): List<PrayerLog>

    @Query("SELECT * FROM prayer_logs WHERE childId = :childId AND date >= :startDate AND date <= :endDate")
    suspend fun getLogsForDateRange(childId: Long, startDate: String, endDate: String): List<PrayerLog>

    // FIXED: Added getAllLogs to match what BackupWorker is asking for
    @Query("SELECT * FROM prayer_logs")
    suspend fun getAllLogs(): List<PrayerLog>

    // Keeping the old name just in case another file uses it
    @Query("SELECT * FROM prayer_logs")
    suspend fun getAllPrayerLogs(): List<PrayerLog>

    @Query("DELETE FROM prayer_logs")
    suspend fun deleteAll()
}