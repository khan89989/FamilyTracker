package com.family.tracker

import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.family.tracker.data.AppDatabase
import com.family.tracker.data.User
import com.family.tracker.databinding.ActivityChildBinding
import com.family.tracker.utils.AutoBackupHelper
import com.family.tracker.utils.PasswordUtils
import com.family.tracker.utils.PrayerAlarmScheduler
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChildActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChildBinding
    private lateinit var db: AppDatabase
    private var userId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChildBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getDatabase(this)
        userId = intent.getLongExtra("USER_ID", -1)

        loadUserData()
        val dateDisplay = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        binding.tvChildDate.text = "Today ($dateDisplay)"
        setupTabs()
        setupBackPressHandler()
        observeBackupStatus()
        AutoBackupHelper.triggerImmediateBackup(this)

        // Ensure prayer alarms are scheduled when the child opens the app
        lifecycleScope.launch(Dispatchers.IO) {
            val settings = db.settingsDao().getSettings()
            if (settings != null && settings.isChildDevice) {
                PrayerAlarmScheduler.scheduleAlarms(this@ChildActivity, settings)
            }
        }

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                AutoBackupHelper.triggerSilentBackup(this@ChildActivity)
            }
        })

        binding.btnHomeChild.setOnClickListener { goToProfilesPage() }
        binding.ivChildProfile.setOnClickListener { showSwitchUserDialog() }
    }

    override fun onStop() {
        AutoBackupHelper.triggerImmediateBackup(this)
        super.onStop()
    }

    private fun observeBackupStatus() {
        WorkManager.getInstance(this)
            .getWorkInfosForUniqueWorkLiveData(AutoBackupHelper.UNIQUE_NAME)
            .observe(this) { infos ->
                val latest = infos.lastOrNull()
                val text = when (latest?.state) {
                    WorkInfo.State.RUNNING -> "⏳ Syncing…"
                    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> "⏳ Queued"
                    WorkInfo.State.SUCCEEDED -> {
                        val t = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                        "✔ Synced $t"
                    }
                    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> "⚠ Not synced"
                    else -> "🔄 Ready"
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

    private fun loadUserData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val user = db.userDao().getUserById(userId)
            withContext(Dispatchers.Main) {
                if (user != null) {
                    binding.tvWelcome.text = "Welcome"
                    binding.tvChildName.text = user.username
                    binding.tvChildPoints.text = "⭐ ${user.points}"
                    if (user.profileImageUri != null) {
                        val imgFile = File(user.profileImageUri)
                        if (imgFile.exists()) {
                            val bm = BitmapFactory.decodeFile(imgFile.absolutePath)
                            binding.ivChildProfile.setImageBitmap(bm)
                        }
                    }
                }
            }
        }
    }

    fun refreshPoints() {
        lifecycleScope.launch(Dispatchers.IO) {
            val user = db.userDao().getUserById(userId)
            withContext(Dispatchers.Main) {
                if (user != null) binding.tvChildPoints.text = "⭐ ${user.points}"
            }
        }
    }

    private fun setupTabs() {
        val pagerAdapter = ChildPagerAdapter(this, userId)
        binding.viewPager.adapter = pagerAdapter
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) { 0 -> "Tasks"; 1 -> "Habits"; 2 -> "H/W"; else -> "Prayers" }
        }.attach()
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                AlertDialog.Builder(this@ChildActivity)
                    .setTitle("Exit App?")
                    .setMessage("What would you like to do?")
                    .setPositiveButton("Exit") { _, _ -> finishAffinity() }
                    .setNeutralButton("Home Screen") { _, _ -> goToProfilesPage() }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        })
    }

    private fun showSwitchUserDialog() {
        lifecycleScope.launch(Dispatchers.IO) {
            val allChildren = db.userDao().getAllChildren().filter { it.id != userId }
            withContext(Dispatchers.Main) {
                if (allChildren.isEmpty()) {
                    Toast.makeText(this@ChildActivity, "No other child profiles found.", Toast.LENGTH_SHORT).show()
                    return@withContext
                }
                val names = allChildren.map { it.username }.toTypedArray()
                AlertDialog.Builder(this@ChildActivity)
                    .setTitle("Switch Profile")
                    .setItems(names) { _, which -> askForPassword(allChildren[which]) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    private fun askForPassword(targetUser: User) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val etPassword = EditText(this).apply {
            hint = "Enter ${targetUser.username}'s Password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        container.addView(etPassword)

        AlertDialog.Builder(this)
            .setTitle("Switch to ${targetUser.username}")
            .setView(container)
            .setPositiveButton("Switch", null)
            .setNegativeButton("Cancel", null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val inputPass = etPassword.text.toString().trim()
                        if (PasswordUtils.hashPassword(inputPass) == targetUser.passwordHash) {
                            val intent = Intent(this@ChildActivity, ChildActivity::class.java)
                            intent.putExtra("USER_ID", targetUser.id)
                            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(intent)
                            finish()
                        } else {
                            Toast.makeText(this@ChildActivity, "Incorrect Password!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }.show()
    }
}