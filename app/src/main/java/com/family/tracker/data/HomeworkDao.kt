package com.family.tracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface HomeworkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHomework(homework: Homework)

    @Update
    suspend fun updateHomework(homework: Homework)

    @Delete
    suspend fun deleteHomework(homework: Homework)

    @Query("DELETE FROM homework WHERE subjectId = :subjectId")
    suspend fun deleteHomeworkBySubject(subjectId: Long)

    @Query("SELECT * FROM homework WHERE childId = :childId AND assignedDate = :date ORDER BY status ASC, id DESC")
    suspend fun getHomeworkForChildByDate(childId: Long, date: String): List<Homework>

    // NEW: Added for BackupWorker
    @Query("SELECT * FROM homework")
    suspend fun getAllHomework(): List<Homework>

    @Query("DELETE FROM homework")
    suspend fun deleteAll()
}