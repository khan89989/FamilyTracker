package com.family.tracker

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.family.tracker.data.AppDatabase
import com.family.tracker.databinding.ActivityEditProfileBinding
import com.family.tracker.utils.AutoBackupHelper
import com.family.tracker.utils.PasswordUtils
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class EditProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditProfileBinding
    private lateinit var db: AppDatabase
    private var userId: Long = -1
    private var isParent: Boolean = false
    private var currentUserImagePath: String? = null

    // Image Picker
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            startCrop(uri)
        }
    }

    // UCrop Result
    private val cropImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val resultUri = UCrop.getOutput(result.data!!)
            if (resultUri != null) {
                val destinationFile = File(filesDir, "profile_${userId}_${System.currentTimeMillis()}.jpg")
                contentResolver.openInputStream(resultUri)?.use { input ->
                    destinationFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                currentUserImagePath = destinationFile.absolutePath
                binding.ivProfileImage.setImageBitmap(BitmapFactory.decodeFile(currentUserImagePath))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getDatabase(this)
        userId = intent.getLongExtra("USER_ID", -1)
        isParent = intent.getBooleanExtra("IS_PARENT", false)

        loadUserData()

        binding.ivProfileImage.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.btnSaveProfile.setOnClickListener {
            saveProfile()
        }
    }

    private fun loadUserData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val user = db.userDao().getUserById(userId)
            withContext(Dispatchers.Main) {
                if (user != null) {
                    binding.etUsername.setText(user.username)
                    currentUserImagePath = user.profileImageUri

                    if (currentUserImagePath != null) {
                        val imgFile = File(currentUserImagePath!!)
                        if (imgFile.exists()) {
                            binding.ivProfileImage.setImageBitmap(BitmapFactory.decodeFile(imgFile.absolutePath))
                        }
                    }
                }
            }
        }
    }

    private fun startCrop(sourceUri: Uri) {
        val destinationUri = Uri.fromFile(File(cacheDir, "cropped_${System.currentTimeMillis()}.jpg"))
        val uCrop = UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(500, 500)
        cropImageLauncher.launch(uCrop.getIntent(this))
    }

    private fun saveProfile() {
        val newUsername = binding.etUsername.text.toString().trim()
        val newPassword = binding.etPassword.text.toString().trim()

        if (newUsername.isEmpty()) {
            Toast.makeText(this, "Username cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val user = db.userDao().getUserById(userId)
            if (user != null) {
                val updatedUser = user.copy(
                    username = newUsername,
                    profileImageUri = currentUserImagePath,
                    passwordHash = if (newPassword.isNotEmpty()) PasswordUtils.hashPassword(newPassword) else user.passwordHash
                )
                db.userDao().updateUser(updatedUser)
                AutoBackupHelper.triggerSilentBackup(this@EditProfileActivity)

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@EditProfileActivity, "Profile Updated!", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }
}