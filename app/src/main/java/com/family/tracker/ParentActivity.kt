package com.family.tracker

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.family.tracker.data.AppDatabase
import com.family.tracker.data.AppSettings
import com.family.tracker.data.User
import com.family.tracker.databinding.ActivityParentBinding
import com.family.tracker.databinding.DialogAddChildBinding
import com.family.tracker.utils.AutoBackupHelper
import com.family.tracker.utils.PasswordUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ParentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityParentBinding
    private lateinit var db: AppDatabase
    private lateinit var adapter: ChildAdapter

    private var currentParentId: Long = -1
    private var isLoadingSettings = false

    private val REQ_PICK_FOLDER = 1001
    private val PREF_BACKUP_TREE_URI = "backup_tree_uri"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityParentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getDatabase(this)
        currentParentId = intent.getLongExtra("USER_ID", -1)

        setupRecyclerView()
        loadParentData()
        loadDeviceSettings()
        setupBackPressHandler()
        observeBackupStatus()
        AutoBackupHelper.triggerImmediateBackup(this)

        binding.btnHomeParent.setOnClickListener { goToProfilesPage() }
        binding.btnLogoutParent.setOnClickListener { goToProfilesPage() }

        binding.btnEditParentProfile.setOnClickListener {
            val intent = Intent(this, EditProfileActivity::class.java)
            intent.putExtra("USER_ID", currentParentId)
            intent.putExtra("IS_PARENT", true)
            startActivity(intent)
        }

        binding.btnAddChild.setOnClickListener { showAddUserDialog("CHILD") }
        binding.btnAddParent.setOnClickListener { showAddUserDialog("PARENT") }
        binding.btnSetupDrive.setOnClickListener { pickBackupFolder() }

        binding.btnPrayerSettings.setOnClickListener {
            startActivity(Intent(this, PrayerSettingsActivity::class.java))
        }

        binding.switchIsChildDevice.setOnCheckedChangeListener { _, isChecked ->
            if (isLoadingSettings) return@setOnCheckedChangeListener
            lifecycleScope.launch(Dispatchers.IO) {
                val settings = db.settingsDao().getSettings() ?: AppSettings()
                db.settingsDao().insertOrUpdate(settings.copy(isChildDevice = isChecked))
                AutoBackupHelper.triggerSilentBackup(this@ParentActivity)
                withContext(Dispatchers.Main) {
                    val msg = if (isChecked) "This phone will now play Adhan and Reminders" else "Child Device mode disabled"
                    Toast.makeText(this@ParentActivity, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadDeviceSettings() {
        lifecycleScope.launch(Dispatchers.IO) {
            val settings = db.settingsDao().getSettings() ?: AppSettings()
            withContext(Dispatchers.Main) {
                isLoadingSettings = true
                binding.switchIsChildDevice.isChecked = settings.isChildDevice
                isLoadingSettings = false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadParentData()
        loadChildrenWithStats()
    }

    override fun onStop() {
        AutoBackupHelper.triggerImmediateBackup(this)
        super.onStop()
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                AlertDialog.Builder(this@ParentActivity)
                    .setTitle("Exit App?")
                    .setMessage("What would you like to do?")
                    .setPositiveButton("Exit") { _, _ -> finishAffinity() }
                    .setNeutralButton("Home Screen") { _, _ -> goToProfilesPage() }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        })
    }

    private fun observeBackupStatus() {
        WorkManager.getInstance(this)
            .getWorkInfosForUniqueWorkLiveData(AutoBackupHelper.UNIQUE_NAME)
            .observe(this) { infos ->
                val latest = infos.lastOrNull()
                val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                val hasBackupFolder = prefs.getString(PREF_BACKUP_TREE_URI, null) != null

                val text = when (latest?.state) {
                    WorkInfo.State.RUNNING -> "⏳ Syncing…"
                    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> "⏳ Queued"
                    WorkInfo.State.SUCCEEDED -> {
                        val t = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                        "✔ Synced $t"
                    }
                    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                        if (!hasBackupFolder) "📁 Setup Backup Folder" else "⚠ Not synced"
                    }
                    else -> {
                        if (!hasBackupFolder) "📁 Setup Backup Folder" else "🔄 Ready"
                    }
                }
                binding.tvBackupStatus.text = text
            }
    }

    private fun goToProfilesPage() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun loadParentData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val parent = db.userDao().getUserById(currentParentId)
            withContext(Dispatchers.Main) {
                if (parent != null) {
                    binding.tvParentName.text = parent.username
                    if (parent.profileImageUri != null) {
                        val imgFile = File(parent.profileImageUri)
                        if (imgFile.exists()) {
                            val bm = BitmapFactory.decodeFile(imgFile.absolutePath)
                            binding.ivParentProfile.setImageBitmap(bm)
                        }
                    } else {
                        binding.ivParentProfile.setImageResource(android.R.drawable.ic_menu_gallery)
                    }
                }
            }
        }
    }

    private fun pickBackupFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQ_PICK_FOLDER)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            val uri = data?.data
            if (uri != null && requestCode == REQ_PICK_FOLDER) {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    getSharedPreferences("app_prefs", MODE_PRIVATE).edit().putString(PREF_BACKUP_TREE_URI, uri.toString()).apply()
                    Toast.makeText(this, "Backup Folder Set!", Toast.LENGTH_SHORT).show()
                    AutoBackupHelper.triggerImmediateBackup(this)
                } catch (e: Exception) {
                    Toast.makeText(this, "Permission error", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = ChildAdapter(
            emptyList(),
            onChildClick = { child ->
                val intent = Intent(this, ChildDetailActivity::class.java)
                intent.putExtra("CHILD_ID", child.id)
                intent.putExtra("CHILD_NAME", child.username)
                startActivity(intent)
            },
            onEditClick = { child ->
                val intent = Intent(this, EditProfileActivity::class.java)
                intent.putExtra("USER_ID", child.id)
                intent.putExtra("IS_PARENT", false)
                startActivity(intent)
            }
        )
        binding.rvChildren.layoutManager = LinearLayoutManager(this)
        binding.rvChildren.adapter = adapter
    }

    private fun loadChildrenWithStats() {
        lifecycleScope.launch(Dispatchers.IO) {
            val users = db.userDao().getAllChildren()
            val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val progressList = mutableListOf<ChildProgress>()

            for (user in users) {
                val tasks = db.taskDao().getTasksForChildByDate(user.id, todayDate)
                val tasksDone = tasks.count { it.status == "DONE" }

                val habits = db.habitDao().getHabitsForChild(user.id)
                var habitsDone = 0
                for (h in habits) {
                    if (db.habitDao().getLogForDate(h.id, todayDate)?.status == "DONE") habitsDone++
                }

                val hw = db.homeworkDao().getHomeworkForChildByDate(user.id, todayDate)
                val hwDone = hw.count { it.status == "DONE" }

                progressList.add(ChildProgress(user, tasksDone, tasks.size, habitsDone, habits.size, hwDone, hw.size))
            }

            withContext(Dispatchers.Main) { adapter.updateData(progressList) }
        }
    }

    private fun showAddUserDialog(role: String) {
        val dialogBinding = DialogAddChildBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (role == "CHILD") "Add New Child" else "Add New Parent")
            .setView(dialogBinding.root)
            .setPositiveButton("Create", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val username = dialogBinding.etNewChildUsername.text.toString().trim()
                val password = dialogBinding.etNewChildPassword.text.toString().trim()
                val confirmPass = dialogBinding.etNewChildConfirmPassword.text.toString().trim()

                if (username.isEmpty() || password.isEmpty()) {
                    Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (password != confirmPass) {
                    Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                lifecycleScope.launch(Dispatchers.IO) {
                    val existing = db.userDao().getUserByUsername(username)
                    if (existing != null) {
                        withContext(Dispatchers.Main) { Toast.makeText(this@ParentActivity, "Username taken!", Toast.LENGTH_SHORT).show() }
                        return@launch
                    }
                    val hash = PasswordUtils.hashPassword(password)
                    db.userDao().insertUser(User(username = username, passwordHash = hash, role = role))
                    AutoBackupHelper.triggerSilentBackup(this@ParentActivity)
                    withContext(Dispatchers.Main) { dialog.dismiss(); loadChildrenWithStats() }
                }
            }
        }
        dialog.show()
    }
}