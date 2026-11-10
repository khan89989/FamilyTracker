package com.family.tracker.utils

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import com.family.tracker.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ApprovalHelper {
    fun requestApproval(
        context: Context,
        actionName: String,
        requireReason: Boolean = false,
        onApproved: (String) -> Unit
    ) {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }

        val inputPassword = EditText(context).apply {
            hint = "Enter Parent Password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        container.addView(inputPassword)

        var inputReason: EditText? = null
        if (requireReason) {
            inputReason = EditText(context).apply {
                hint = "Enter Reason for Skipping"
                inputType = InputType.TYPE_CLASS_TEXT
                setPadding(0, 24, 0, 0)
            }
            container.addView(inputReason)
        }

        AlertDialog.Builder(context)
            .setTitle("Parent Approval: $actionName")
            .setMessage("Hand the device to a parent to approve.")
            .setView(container)
            .setPositiveButton("Approve", null)
            .setNegativeButton("Cancel", null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val pwd = inputPassword.text.toString().trim()
                        val reason = inputReason?.text.toString().trim() ?: ""

                        if (pwd.isEmpty()) return@setOnClickListener
                        if (requireReason && reason.isEmpty()) {
                            Toast.makeText(context, "Reason is mandatory for skipping.", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }

                        CoroutineScope(Dispatchers.IO).launch {
                            val db = AppDatabase.getDatabase(context)
                            val parents = db.userDao().getAllUsers().filter { it.role == "PARENT" }
                            val hash = PasswordUtils.hashPassword(pwd)

                            if (parents.any { it.passwordHash == hash }) {
                                withContext(Dispatchers.Main) {
                                    dismiss()
                                    onApproved(reason)
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Invalid Parent Password", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            }.show()
    }
}