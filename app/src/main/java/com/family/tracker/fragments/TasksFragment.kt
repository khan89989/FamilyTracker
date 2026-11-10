package com.family.tracker.fragments

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.family.tracker.R
import com.family.tracker.data.AppDatabase
import com.family.tracker.data.Task
import com.family.tracker.databinding.FragmentTasksBinding
import com.family.tracker.utils.AutoBackupHelper
import com.family.tracker.utils.PasswordUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TasksFragment : Fragment() {

    private var _binding: FragmentTasksBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: AppDatabase
    private var childId: Long = -1
    private var dateString: String = ""
    private lateinit var adapter: TaskAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTasksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        childId = arguments?.getLong("CHILD_ID") ?: arguments?.getLong("USER_ID") ?: -1
        dateString = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        db = AppDatabase.getDatabase(requireContext())

        adapter = TaskAdapter(emptyList())
        binding.rvTasks.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTasks.adapter = adapter

        loadTasks()
    }

    override fun onResume() {
        super.onResume()
        loadTasks()
    }

    private fun loadTasks() {
        lifecycleScope.launch(Dispatchers.IO) {
            val tasks = db.taskDao().getTasksForChildByDate(childId, dateString)
            withContext(Dispatchers.Main) {
                adapter.updateData(tasks)
            }
        }
    }

    private fun showMotivationalPopup(isSuccess: Boolean) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_motivational, null)
        val tvEmoji = dialogView.findViewById<TextView>(R.id.tvEmoji)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tvMessage)
        if (isSuccess) {
            tvEmoji.text = "😃"
            tvMessage.text = "Great Job!"
        } else {
            tvEmoji.text = "🤔"
            tvMessage.text = "Don't Worry!"
        }
        AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("OK") { d, _ -> d.dismiss() }
            .setCancelable(false)
            .show()
    }

    private fun showParentPasswordDialog(onSuccess: () -> Unit) {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val etPassword = EditText(requireContext()).apply {
            hint = "Parent Password to Approve"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        container.addView(etPassword)

        AlertDialog.Builder(requireContext())
            .setTitle("Parent Approval Required")
            .setView(container)
            .setPositiveButton("Approve", null)
            .setNegativeButton("Cancel", null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val inputPass = etPassword.text.toString().trim()
                        lifecycleScope.launch(Dispatchers.IO) {
                            val parents = db.userDao().getAllUsers().filter { it.role == "PARENT" }
                            val isValid = parents.any { PasswordUtils.hashPassword(inputPass) == it.passwordHash }

                            withContext(Dispatchers.Main) {
                                if (isValid) {
                                    onSuccess()
                                    dismiss()
                                } else {
                                    Toast.makeText(requireContext(), "Incorrect parent password!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            }.show()
    }

    private fun updateTaskStatus(task: Task, newStatus: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val completionTime = if (newStatus == "DONE") System.currentTimeMillis() else 0L
            db.taskDao().updateTaskStatus(task.id, newStatus, completionTime)

            // FIXED: Changed 5 and -5 to 1.0 and -1.0 for Double
            if (newStatus == "DONE" && task.status != "DONE") {
                db.userDao().updatePoints(childId, 1.0)
            } else if (newStatus != "DONE" && task.status == "DONE") {
                db.userDao().updatePoints(childId, -1.0)
            }

            AutoBackupHelper.triggerSilentBackup(requireContext())
            withContext(Dispatchers.Main) {
                loadTasks()
                (activity as? com.family.tracker.ChildActivity)?.refreshPoints()
                if (newStatus == "DONE") showMotivationalPopup(true)
            }
        }
    }

    inner class TaskAdapter(private var tasks: List<Task>) : RecyclerView.Adapter<TaskAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val cbTaskDone: CheckBox = view.findViewById(R.id.cbTaskDone)
            val tvTaskTitle: TextView = view.findViewById(R.id.tvTaskTitle)
            val tvTaskDescription: TextView = view.findViewById(R.id.tvTaskDescription)
            val tvTaskStatus: TextView = view.findViewById(R.id.tvTaskStatus)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val task = tasks[position]

            holder.tvTaskTitle.text = task.title
            holder.tvTaskDescription.text = task.description

            // TIMESTAMP LOGIC
            val timeStr = if (task.completionTimeMillis > 0L) {
                SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(task.completionTimeMillis))
            } else ""

            holder.tvTaskStatus.text = when (task.status) {
                "DONE" -> "DONE ($timeStr)"
                "SKIPPED" -> "SKIPPED"
                else -> "PENDING"
            }

            holder.tvTaskStatus.setTextColor(
                when (task.status) {
                    "DONE" -> Color.parseColor("#4CAF50")
                    "SKIPPED" -> Color.parseColor("#2196F3")
                    else -> Color.parseColor("#FF9800")
                }
            )

            holder.cbTaskDone.setOnCheckedChangeListener(null)
            holder.cbTaskDone.isChecked = (task.status == "DONE")

            holder.cbTaskDone.setOnClickListener {
                val isChecked = holder.cbTaskDone.isChecked
                val newStatus = if (isChecked) "DONE" else "PENDING"

                holder.cbTaskDone.isChecked = !isChecked // Revert temporarily for approval

                showParentPasswordDialog {
                    updateTaskStatus(task, newStatus)
                }
            }

            holder.itemView.setOnClickListener {
                val newStatus = if (task.status == "DONE") "PENDING" else "DONE"
                showParentPasswordDialog {
                    updateTaskStatus(task, newStatus)
                }
            }
        }

        override fun getItemCount() = tasks.size

        fun updateData(newTasks: List<Task>) {
            tasks = newTasks
            notifyDataSetChanged()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}