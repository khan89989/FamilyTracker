package com.family.tracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task)

    @Update
    suspend fun updateTask(task: Task)

    // THIS IS THE LINE THAT WAS MISSING
    @Query("UPDATE tasks SET status = :status, completionTimeMillis = :completionTimeMillis WHERE id = :taskId")
    suspend fun updateTaskStatus(taskId: Long, status: String, completionTimeMillis: Long)

    @Delete
    suspend fun deleteTask(task: Task)

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun deleteTaskById(taskId: Long)

    @Query("SELECT * FROM tasks WHERE childId = :childId AND assignedDate = :date")
    suspend fun getTasksForChildByDate(childId: Long, date: String): List<Task>

    @Query("SELECT * FROM tasks")
    suspend fun getAllTasks(): List<Task>

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()
}