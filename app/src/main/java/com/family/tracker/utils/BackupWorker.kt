package com.family.tracker.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.family.tracker.data.*
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStreamWriter
import java.util.*

class BackupWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(applicationContext)

            // 1. Gather all data
            val users = db.userDao().getAllUsers()
            val tasks = db.taskDao().getAllTasks()
            val habits = db.habitDao().getAllHabits()
            val habitLogs = db.habitDao().getAllLogs() // Using the method fixed earlier
            val subjects = db.subjectDao().getAllSubjects()
            val homework = db.homeworkDao().getAllHomework()
            val prayerLogs = db.prayerDao().getAllLogs()
            val settings = db.settingsDao().getSettings()

            // 2. Map to Backup Data classes
            val backupUsers = users.map { user ->
                var base64Image: String? = null
                if (user.profileImageUri != null) {
                    try {
                        val file = File(user.profileImageUri)
                        if (file.exists()) {
                            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                            val baos = ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos)
                            val bytes = baos.toByteArray()
                            base64Image = Base64.encodeToString(bytes, Base64.DEFAULT)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                BackupUser(
                    id = user.id,
                    username = user.username,
                    passwordHash = user.passwordHash,
                    role = user.role,
                    points = user.points, // Now maps a Double
                    profileImageBase64 = base64Image,
                    backupPassword = user.backupPassword
                )
            }

            val backupTasks = tasks.map {
                BackupTask(it.id, it.childId, it.title, it.assignedDate, it.description, it.status, it.startTimeMillis, it.endTimeMillis, it.completionTimeMillis, it.hasAlarm)
            }
            val backupHabits = habits.map {
                BackupHabit(it.id, it.childId, it.title, it.prayerReference, it.offsetMinutes, it.repeatType, it.repeatDays)
            }
            val backupHabitLogs = habitLogs.map {
                BackupHabitLog(it.id, it.habitId, it.date, it.status, it.completionTimeMillis)
            }
            val backupSubjects = subjects.map {
                BackupSubject(it.id, it.childId, it.name)
            }
            val backupHomework = homework.map {
                BackupHomework(it.id, it.childId, it.subjectId, it.title, it.assignedDate, it.notes, it.status, it.completionTimeMillis, it.detailsText, it.detailsUri, it.needsHelp, it.helpText, it.helpPhotoUri, it.helpFileUri)
            }
            val backupPrayerLogs = prayerLogs.map {
                BackupPrayerLog(it.id, it.childId, it.date, it.prayerName, it.status, it.reason)
            }

            val backupData = BackupData(
                users = backupUsers,
                tasks = backupTasks,
                habits = backupHabits,
                habitLogs = backupHabitLogs,
                subjects = backupSubjects,
                homework = backupHomework,
                prayerLogs = backupPrayerLogs,
                settings = settings
            )

            // 3. Convert to JSON and encrypt
            val gson = GsonBuilder().create()
            val jsonString = gson.toJson(backupData)

            val backupPassword = users.firstOrNull { it.role == "PARENT" }?.backupPassword ?: "123456"
            val encryptedString = CryptoUtils.encrypt(jsonString, backupPassword) ?: return@withContext Result.failure()

            // 4. Save to Google Drive / Backup File (using SAF)
            val prefs = applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val fileUriString = prefs.getString("backup_file_uri", null)
            val treeUriString = prefs.getString("backup_tree_uri", null)

            var written = false
            if (fileUriString != null) {
                try {
                    val fileUri = Uri.parse(fileUriString)
                    applicationContext.contentResolver.openOutputStream(fileUri, "wt")?.use { outputStream ->
                        outputStream.write(encryptedString.toByteArray())
                        written = true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (!written && treeUriString != null) {
                try {
                    val treeUri = Uri.parse(treeUriString)
                    val root = DocumentFile.fromTreeUri(applicationContext, treeUri)
                    if (root != null) {
                        val backupFileName = "FamilyTracker_Backup.json"
                        var file = root.findFile(backupFileName)
                        if (file == null) {
                            file = root.createFile("application/json", backupFileName)
                        }
                        if (file != null) {
                            applicationContext.contentResolver.openOutputStream(file.uri)?.use { outputStream ->
                                val writer = OutputStreamWriter(outputStream)
                                writer.write(encryptedString)
                                writer.close()
                                written = true
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (!written) return@withContext Result.failure()
            return@withContext Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext Result.failure()
        }
    }
}

// ---------------------------------------------------------
// BACKUP DATA CLASSES (Moved / Updated points to Double)
// ---------------------------------------------------------

data class BackupData(
    val users: List<BackupUser>,
    val tasks: List<BackupTask>,
    val habits: List<BackupHabit>,
    val habitLogs: List<BackupHabitLog>,
    val subjects: List<BackupSubject>,
    val homework: List<BackupHomework>,
    val prayerLogs: List<BackupPrayerLog>,
    val settings: AppSettings?
)

data class BackupUser(
    val id: Long,
    val username: String,
    val passwordHash: String,
    val role: String,
    val points: Double, // <--- FIXED: Changed from Int to Double
    val profileImageBase64: String?,
    val backupPassword: String?
)

data class BackupTask(
    val id: Long, val childId: Long, val title: String, val assignedDate: String, val description: String,
    val status: String, val startTimeMillis: Long, val endTimeMillis: Long, val completionTimeMillis: Long, val hasAlarm: Boolean
)

data class BackupHabit(
    val id: Long, val childId: Long, val title: String, val prayerReference: String,
    val offsetMinutes: Int, val repeatType: String, val repeatDays: String
)

data class BackupHabitLog(
    val id: Long, val habitId: Long, val date: String, val status: String, val completionTimeMillis: Long
)

data class BackupSubject(
    val id: Long, val childId: Long, val name: String
)

data class BackupHomework(
    val id: Long, val childId: Long, val subjectId: Long, val title: String, val assignedDate: String,
    val notes: String, val status: String, val completionTimeMillis: Long, val detailsText: String,
    val detailsUri: String?, val needsHelp: Boolean, val helpText: String?, val helpPhotoUri: String?, val helpFileUri: String?
)

data class BackupPrayerLog(
    val id: Long, val childId: Long, val date: String, val prayerName: String, val status: String, val reason: String
)