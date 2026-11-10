package com.family.tracker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.family.tracker.data.*
import com.family.tracker.databinding.ActivityLoginBinding
import com.family.tracker.utils.CryptoUtils
import com.family.tracker.utils.NetworkUtils
import com.family.tracker.utils.PasswordUtils
import com.family.tracker.utils.PrayerAlarmScheduler
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var db: AppDatabase
    private lateinit var profileAdapter: ProfileAdapter
    private val REQ_RESTORE_FILE = 3001

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getDatabase(this)

        profileAdapter = ProfileAdapter(emptyList()) { user ->
            showPasswordDialog(user)
        }

        binding.rvProfiles.layoutManager = GridLayoutManager(this, 2)
        binding.rvProfiles.adapter = profileAdapter

        binding.btnCreateFirstParent.setOnClickListener { createFirstParent() }
        binding.btnRestoreBackup.setOnClickListener { pickBackupFile() }
    }

    override fun onResume() {
        super.onResume()
        checkInternetAndLoad()
    }

    private fun checkInternetAndLoad() {
        if (!NetworkUtils.isOnline(this)) {
            showNoInternetDialog()
        } else {
            loadUsers()
        }
    }

    private fun showNoInternetDialog() {
        AlertDialog.Builder(this)
            .setTitle("No Internet Connection")
            .setMessage("This app requires an internet connection to retrieve and sync data with Google Drive. Please connect to Wi-Fi or Mobile Data.")
            .setCancelable(false)
            .setPositiveButton("Retry") { _, _ -> checkInternetAndLoad() }
            .setNegativeButton("Exit App") { _, _ -> finishAffinity() }
            .show()
    }

    private fun loadUsers() {
        lifecycleScope.launch(Dispatchers.IO) {
            val allUsers = db.userDao().getAllUsers()
            withContext(Dispatchers.Main) {
                if (allUsers.isEmpty()) {
                    binding.layoutCreateParent.visibility = View.VISIBLE
                    binding.layoutProfileGrid.visibility = View.GONE
                } else {
                    binding.layoutCreateParent.visibility = View.GONE
                    binding.layoutProfileGrid.visibility = View.VISIBLE
                    profileAdapter.updateData(allUsers)
                }
            }
        }
    }

    private fun createFirstParent() {
        val username = binding.etNewParentUsername.text.toString().trim()
        val password = binding.etNewParentPassword.text.toString().trim()
        val confirmPass = binding.etNewParentConfirmPassword.text.toString().trim()
        val backupPass = binding.etBackupPassword.text.toString().trim()

        if (username.isEmpty() || password.isEmpty() || backupPass.isEmpty()) {
            Toast.makeText(this, "Please fill all fields including backup password", Toast.LENGTH_SHORT).show()
            return
        }
        if (password != confirmPass) {
            Toast.makeText(this, "Passwords do not match!", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val hash = PasswordUtils.hashPassword(password)
            val parent = User(username = username, passwordHash = hash, role = "PARENT", backupPassword = backupPass)
            db.userDao().insertUser(parent)

            withContext(Dispatchers.Main) {
                Toast.makeText(this@LoginActivity, "Parent Account Created!", Toast.LENGTH_LONG).show()
                loadUsers()
            }
        }
    }

    private fun pickBackupFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        startActivityForResult(intent, REQ_RESTORE_FILE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_RESTORE_FILE && resultCode == Activity.RESULT_OK) {
            val uri = data?.data
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit()
                    .putString("backup_file_uri", uri.toString())
                    .apply()

                promptRestorePassword(uri)
            }
        }
    }

    private fun promptRestorePassword(uri: Uri) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val etPass = EditText(this).apply {
            hint = "Enter Backup Password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        container.addView(etPass)

        AlertDialog.Builder(this)
            .setTitle("Decrypt Backup")
            .setView(container)
            .setPositiveButton("Restore", null)
            .setNegativeButton("Cancel", null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val pwd = etPass.text.toString().trim()
                        if (pwd.isNotEmpty()) {
                            restoreBackup(uri, pwd)
                            dismiss()
                        }
                    }
                }
            }.show()
    }

    private fun restoreBackup(uri: Uri, password: String) {
        Toast.makeText(this, "Decrypting and restoring...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val encryptedString = inputStream?.bufferedReader().use { it?.readText() }
                inputStream?.close()

                if (encryptedString.isNullOrEmpty()) throw Exception("Empty file")

                val jsonString = CryptoUtils.decrypt(encryptedString, password)
                if (jsonString == null) throw Exception("Invalid password or corrupt file")

                val root = JsonParser.parseString(jsonString).asJsonObject

                db.userDao().deleteAllChildren()
                db.userDao().deleteAllParents()
                db.taskDao().deleteAll()
                db.habitDao().deleteAllHabits()
                db.habitDao().deleteAllLogs()
                db.subjectDao().deleteAll()
                db.homeworkDao().deleteAll()
                db.prayerDao().deleteAll()
                db.settingsDao().deleteAll()

                if (root.has("settings") && !root.get("settings").isJsonNull) {
                    val obj = root.getAsJsonObject("settings")
                    val settings = AppSettings(
                        id = obj.get("id")?.asInt ?: 1,
                        latitude = obj.get("latitude")?.asDouble ?: 21.4225,
                        longitude = obj.get("longitude")?.asDouble ?: 39.8262,
                        cityName = obj.get("cityName")?.asString ?: "Makkah",
                        methodId = obj.get("methodId")?.asString ?: "MUSLIM_WORLD_LEAGUE",
                        homeworkReminder = obj.get("homeworkReminder")?.asBoolean ?: false,
                        prayerReminder = obj.get("prayerReminder")?.asBoolean ?: false,
                        isChildDevice = obj.get("isChildDevice")?.asBoolean ?: false,
                        enabledAdhans = obj.get("enabledAdhans")?.asString ?: "Fajr,Dhuhr,Asr,Maghrib,Isha"
                    )
                    db.settingsDao().insertOrUpdate(settings)
                }

                if (root.has("users") && !root.get("users").isJsonNull) {
                    for (element in root.getAsJsonArray("users")) {
                        val obj = element.asJsonObject
                        val id = obj.get("id")?.asLong ?: 0L
                        val username = obj.get("username")?.asString ?: "Unknown"
                        val passwordHash = obj.get("passwordHash")?.asString ?: ""
                        val role = obj.get("role")?.asString ?: "CHILD"

                        // FIXED: Changed to asDouble and 0.0
                        val points = obj.get("points")?.asDouble ?: 0.0

                        val backupPasswordStr = obj.get("backupPassword")?.asString ?: password
                        var newImagePath: String? = null

                        val profileImageBase64 = obj.get("profileImageBase64")?.asString
                        if (!profileImageBase64.isNullOrEmpty()) {
                            try {
                                val imageBytes = Base64.decode(profileImageBase64, Base64.DEFAULT)
                                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                                val file = File(filesDir, "profile_${id}.jpg")
                                val outputStream = FileOutputStream(file)
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, outputStream)
                                outputStream.close()
                                newImagePath = file.absolutePath
                            } catch (e: Exception) { }
                        }

                        db.userDao().insertUser(
                            User(id = id, username = username, passwordHash = passwordHash, role = role, profileImageUri = newImagePath, backupPassword = backupPasswordStr, points = points)
                        )
                    }
                }

                if (root.has("subjects") && !root.get("subjects").isJsonNull) {
                    for (element in root.getAsJsonArray("subjects")) {
                        val obj = element.asJsonObject
                        db.subjectDao().insertSubject(
                            Subject(id = obj.get("id")?.asLong ?: 0L, childId = obj.get("childId")?.asLong ?: 0L, name = obj.get("name")?.asString ?: "Unknown Subject")
                        )
                    }
                }

                if (root.has("tasks") && !root.get("tasks").isJsonNull) {
                    for (element in root.getAsJsonArray("tasks")) {
                        val obj = element.asJsonObject
                        db.taskDao().insertTask(
                            Task(
                                id = obj.get("id")?.asLong ?: 0L, childId = obj.get("childId")?.asLong ?: 0L,
                                title = obj.get("title")?.asString ?: "Untitled Task", assignedDate = obj.get("assignedDate")?.asString ?: "",
                                description = obj.get("description")?.asString ?: "", status = obj.get("status")?.asString ?: "PENDING",
                                startTimeMillis = obj.get("startTimeMillis")?.asLong ?: 0L, endTimeMillis = obj.get("endTimeMillis")?.asLong ?: 0L,
                                completionTimeMillis = obj.get("completionTimeMillis")?.asLong ?: 0L, hasAlarm = obj.get("hasAlarm")?.asBoolean ?: false
                            )
                        )
                    }
                }

                if (root.has("habits") && !root.get("habits").isJsonNull) {
                    for (element in root.getAsJsonArray("habits")) {
                        val obj = element.asJsonObject
                        db.habitDao().insertHabit(
                            Habit(
                                id = obj.get("id")?.asLong ?: 0L, childId = obj.get("childId")?.asLong ?: 0L,
                                title = obj.get("title")?.asString ?: "Untitled Habit", prayerReference = obj.get("prayerReference")?.asString ?: "None",
                                offsetMinutes = obj.get("offsetMinutes")?.asInt ?: 0, repeatType = obj.get("repeatType")?.asString ?: "DAILY",
                                repeatDays = obj.get("repeatDays")?.asString ?: ""
                            )
                        )
                    }
                }

                if (root.has("habitLogs") && !root.get("habitLogs").isJsonNull) {
                    for (element in root.getAsJsonArray("habitLogs")) {
                        val obj = element.asJsonObject
                        db.habitDao().insertLog(
                            HabitLog(id = obj.get("id")?.asLong ?: 0L, habitId = obj.get("habitId")?.asLong ?: 0L, date = obj.get("date")?.asString ?: "", status = obj.get("status")?.asString ?: "PENDING", completionTimeMillis = obj.get("completionTimeMillis")?.asLong ?: 0L)
                        )
                    }
                }

                if (root.has("homework") && !root.get("homework").isJsonNull) {
                    for (element in root.getAsJsonArray("homework")) {
                        val obj = element.asJsonObject
                        db.homeworkDao().insertHomework(
                            Homework(
                                id = obj.get("id")?.asLong ?: 0L, childId = obj.get("childId")?.asLong ?: 0L,
                                subjectId = obj.get("subjectId")?.asLong ?: 0L, title = obj.get("title")?.asString ?: "Untitled",
                                assignedDate = obj.get("assignedDate")?.asString ?: "", notes = obj.get("notes")?.asString ?: "",
                                status = obj.get("status")?.asString ?: "PENDING", completionTimeMillis = obj.get("completionTimeMillis")?.asLong ?: 0L,
                                detailsText = obj.get("detailsText")?.asString ?: "", detailsUri = obj.get("detailsUri")?.asString ?: null,
                                needsHelp = obj.get("needsHelp")?.asBoolean ?: false, helpText = obj.get("helpText")?.asString ?: null,
                                helpPhotoUri = obj.get("helpPhotoUri")?.asString ?: null, helpFileUri = obj.get("helpFileUri")?.asString ?: null
                            )
                        )
                    }
                }

                if (root.has("prayerLogs") && !root.get("prayerLogs").isJsonNull) {
                    for (element in root.getAsJsonArray("prayerLogs")) {
                        val obj = element.asJsonObject
                        db.prayerDao().insertOrUpdateLog(
                            PrayerLog(id = obj.get("id")?.asLong ?: 0L, childId = obj.get("childId")?.asLong ?: 0L, date = obj.get("date")?.asString ?: "", prayerName = obj.get("prayerName")?.asString ?: "Unknown", status = obj.get("status")?.asString ?: "PENDING", reason = obj.get("reason")?.asString ?: "")
                        )
                    }
                }

                val restoredSettings = db.settingsDao().getSettings()
                if (restoredSettings != null && restoredSettings.isChildDevice) {
                    PrayerAlarmScheduler.scheduleAlarms(this@LoginActivity, restoredSettings)
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LoginActivity, "Backup Restored Successfully!", Toast.LENGTH_LONG).show()
                    loadUsers()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    AlertDialog.Builder(this@LoginActivity)
                        .setTitle("Restore Failed")
                        .setMessage("Could not retrieve data from the backup file. Error: ${e.message}")
                        .setCancelable(false)
                        .setPositiveButton("Retry") { _, _ -> pickBackupFile() }
                        .setNegativeButton("Exit App") { _, _ -> finishAffinity() }
                        .show()
                }
            }
        }
    }

    private fun showPasswordDialog(user: User) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val etPassword = EditText(this).apply {
            hint = "Enter Password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        container.addView(etPassword)

        AlertDialog.Builder(this)
            .setTitle("Login as ${user.username}")
            .setView(container)
            .setPositiveButton("Login", null)
            .setNegativeButton("Cancel", null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val inputPass = etPassword.text.toString().trim()
                        val inputHash = PasswordUtils.hashPassword(inputPass)

                        if (inputHash == user.passwordHash) {
                            if (user.role == "PARENT") {
                                val intent = Intent(this@LoginActivity, ParentActivity::class.java)
                                intent.putExtra("USER_ID", user.id)
                                startActivity(intent)
                            } else {
                                val intent = Intent(this@LoginActivity, ChildActivity::class.java)
                                intent.putExtra("USER_ID", user.id)
                                startActivity(intent)
                            }
                            finish()
                        } else {
                            Toast.makeText(this@LoginActivity, "Incorrect Password!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }.show()
    }
}