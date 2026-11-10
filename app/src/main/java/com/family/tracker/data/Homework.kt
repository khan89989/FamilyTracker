package com.family.tracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "homework")
data class Homework(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val childId: Long,
    val subjectId: Long,
    val title: String,
    val assignedDate: String,
    val notes: String,
    val status: String = "PENDING", // PENDING, DONE, NEEDS_HELP
    val completionTimeMillis: Long = 0L,
    val detailsText: String = "",
    val detailsUri: String? = null,
    val needsHelp: Boolean = false,
    val helpText: String? = null,
    val helpPhotoUri: String? = null, // NEW
    val helpFileUri: String? = null   // NEW
)