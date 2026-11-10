package com.family.tracker

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.family.tracker.data.BackupData
import com.family.tracker.databinding.ActivityParentBackupViewerBinding
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ParentBackupViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityParentBackupViewerBinding
    private val REQ_PICK_FILE = 2001
    private val PREF_BACKUP_FILE_URI = "backup_file_uri"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityParentBackupViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val savedUri = getSharedPreferences("app_prefs", MODE_PRIVATE).getString(PREF_BACKUP_FILE_URI, null)
        if (savedUri != null) {
            loadBackupFile(Uri.parse(savedUri))
        } else {
            binding.tvBackupDate.text = "No backup linked. Please select a file."
        }

        binding.btnPickBackupFile.text = "Refresh / Select Backup File"
        binding.btnPickBackupFile.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
            }
            startActivityForResult(intent, REQ_PICK_FILE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK_FILE && resultCode == Activity.RESULT_OK) {
            val uri = data?.data
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    getSharedPreferences("app_prefs", MODE_PRIVATE).edit().putString(PREF_BACKUP_FILE_URI, uri.toString()).apply()
                    loadBackupFile(uri)
                } catch (e: Exception) {
                    Toast.makeText(this, "Failed to persist file permission.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun loadBackupFile(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val jsonString = inputStream?.bufferedReader().use { it?.readText() }
            inputStream?.close()

            if (jsonString.isNullOrEmpty()) {
                Toast.makeText(this, "File is empty", Toast.LENGTH_SHORT).show()
                return
            }

            val backupData = Gson().fromJson(jsonString, BackupData::class.java)
            displayBackupData(backupData)

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to read file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun displayBackupData(data: BackupData) {
        val dateStr = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault()).format(Date(data.generatedAt))
        binding.tvBackupDate.text = "Last Backup Sync: $dateStr"
        binding.containerBackupContent.removeAllViews()

        // Safe call used here (data.users is nullable)
        val users = data.users ?: emptyList()
        val children = users.filter { it.role == "CHILD" }

        if (children.isEmpty()) {
            addText("No children found in this backup.", 16f, isBold = true)
            return
        }

        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

        for (child in children) {
            addSectionHeader("👦 ${child.username}")

            // --- HABITS ---
            val childHabits = data.habits?.filter { it.childId == child.id } ?: emptyList()
            addSubHeader("Daily Habits:")
            if (childHabits.isEmpty()) addText("No habits assigned.", 14f)

            val habitLogs = data.habitLogs ?: emptyList()
            for (habit in childHabits) {
                val log = habitLogs.filter { it.habitId == habit.id }.maxByOrNull { it.date }
                val status = log?.status ?: "PENDING"
                val dateText = if (log != null) " (${log.date})" else ""
                addText("${if (status == "DONE") "✅" else "⏳"} ${habit.title} - $status$dateText", 14f)
            }

            // --- HOMEWORK ---
            val childHomework = data.homework?.filter { it.childId == child.id } ?: emptyList()
            addSubHeader("Homework:")
            if (childHomework.isEmpty()) addText("No homework assigned.", 14f)

            val subjects = data.subjects ?: emptyList()
            for (hw in childHomework) {
                val subjectName = subjects.find { it.id == hw.subjectId }?.name ?: "Unknown Subject"

                // UPDATED: Removed difficulty, added new statuses and timestamps
                val icon = when (hw.status) {
                    "DONE" -> "✅"
                    "NEED_HELP" -> "🟡"
                    else -> "⏳"
                }

                var statusStr = hw.status.replace("_", " ")
                if (hw.completionTimeMillis > 0) {
                    val timeStr = timeFormat.format(Date(hw.completionTimeMillis))
                    statusStr = if (hw.status == "DONE") "Completed at $timeStr" else "Need Help at $timeStr"
                }

                val noteText = if (hw.notes.isNotEmpty()) "\n   📝 Note: ${hw.notes}" else ""
                addText("$icon $subjectName: ${hw.title} - $statusStr$noteText", 14f)
            }

            // --- TASKS ---
            val childTasks = data.tasks?.filter { it.childId == child.id } ?: emptyList()
            addSubHeader("Tasks:")
            if (childTasks.isEmpty()) addText("No tasks assigned.", 14f)
            for (task in childTasks) {
                val icon = if (task.status == "DONE") "✅" else "⏳"

                var statusText = ""
                if (task.status == "DONE") {
                    val compTime = if (task.completionTimeMillis > 0) {
                        timeFormat.format(Date(task.completionTimeMillis))
                    } else "Done"
                    statusText = "Completed at $compTime"
                } else {
                    statusText = "Pending"
                }

                var timeText = ""
                if (task.startTimeMillis > 0 && task.endTimeMillis > 0) {
                    val start = timeFormat.format(Date(task.startTimeMillis))
                    val end = timeFormat.format(Date(task.endTimeMillis))
                    timeText = "\n   🕒 Time: $start - $end"
                }

                val descText = if (task.description.isNotEmpty()) "\n   📝 Desc: ${task.description}" else ""

                addText("$icon ${task.title} - $statusText$timeText$descText", 14f)
            }

            // --- PRAYERS ---
            val childPrayers = data.prayerLogs?.filter { it.childId == child.id } ?: emptyList()
            addSubHeader("Prayers:")
            if (childPrayers.isEmpty()) addText("No prayer logs found.", 14f)
            for (prayer in childPrayers) {
                val icon = if (prayer.status == "OFFERED") "✅" else "❌"
                val reasonText = if (prayer.reason.isNotEmpty()) " (Reason: ${prayer.reason})" else ""
                addText("$icon ${prayer.prayerName} on ${prayer.date} - ${prayer.status}$reasonText", 14f)
            }

            addDivider()
        }
    }

    private fun addSectionHeader(text: String) {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = 20f
        tv.setTypeface(null, Typeface.BOLD)
        tv.setPadding(0, 16, 0, 8)
        binding.containerBackupContent.addView(tv)
    }

    private fun addSubHeader(text: String) {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = 16f
        tv.setTypeface(null, Typeface.BOLD)
        tv.setPadding(0, 12, 0, 4)
        binding.containerBackupContent.addView(tv)
    }

    private fun addText(text: String, size: Float, isBold: Boolean = false) {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = size
        if (isBold) tv.setTypeface(null, Typeface.BOLD)
        tv.setPadding(16, 4, 0, 4)
        binding.containerBackupContent.addView(tv)
    }

    private fun addDivider() {
        val divider = View(this)
        divider.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2).apply { setMargins(0, 16, 0, 16) }
        divider.setBackgroundColor(0xFFCCCCCC.toInt())
        binding.containerBackupContent.addView(divider)
    }
}