package com.family.tracker.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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
import com.family.tracker.databinding.FragmentParentTasksBinding
import com.family.tracker.utils.AutoBackupHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ParentTasksFragment : Fragment() {

    private var _binding: FragmentParentTasksBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: AppDatabase
    private var childId: Long = -1
    private var dateString: String = ""
    private lateinit var adapter: ParentTaskAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentParentTasksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        childId = arguments?.getLong("CHILD_ID") ?: arguments?.getLong("USER_ID") ?: -1
        dateString = arguments?.getString("DATE_STRING") ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        db = AppDatabase.getDatabase(requireContext())

        adapter = ParentTaskAdapter(emptyList())
        binding.rvParentTasks.layoutManager = LinearLayoutManager(requireContext())
        binding.rvParentTasks.adapter = adapter

        binding.btnAddTask.setOnClickListener { showAddTaskDialog() }

        loadTasks()
    }

    override fun onResume() {
        super.onResume()
        loadTasks()
    }

    private fun loadTasks() {
        lifecycleScope.launch(Dispatchers.IO) {
            val tasks = db.taskDao().getTasksForChildByDate(childId, dateString)
            withContext(Dispatchers.Main) { adapter.updateData(tasks) }
        }
    }

    private fun showAddTaskDialog() {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val etTitle = EditText(requireContext()).apply { hint = "Task Title" }
        val etDesc = EditText(requireContext()).apply { hint = "Description (Optional)" }
        container.addView(etTitle)
        container.addView(etDesc)

        AlertDialog.Builder(requireContext())
            .setTitle("Add Task")
            .setView(container)
            .setPositiveButton("Add", null)
            .setNegativeButton("Cancel", null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val title = etTitle.text.toString().trim()
                        val desc = etDesc.text.toString().trim()
                        if (title.isNotEmpty()) {
                            lifecycleScope.launch(Dispatchers.IO) {
                                db.taskDao().insertTask(Task(childId = childId, title = title, assignedDate = dateString, description = desc))
                                AutoBackupHelper.triggerSilentBackup(requireContext())
                                withContext(Dispatchers.Main) {
                                    loadTasks()
                                    dismiss()
                                }
                            }
                        } else {
                            Toast.makeText(requireContext(), "Title required", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }.show()
    }

    private fun resetTask(task: Task) {
        lifecycleScope.launch(Dispatchers.IO) {
            db.taskDao().updateTaskStatus(task.id, "PENDING", 0L)
            AutoBackupHelper.triggerSilentBackup(requireContext())
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "Task status reset", Toast.LENGTH_SHORT).show()
                loadTasks()
            }
        }
    }

    private fun showTaskOptions(task: Task) {
        val options = arrayOf("Reset Status to Pending", "Delete Task")
        AlertDialog.Builder(requireContext())
            .setTitle("Manage: ${task.title}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> resetTask(task)
                    1 -> showDeleteConfirmation(task)
                }
            }
            .show()
    }

    private fun showDeleteConfirmation(task: Task) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Task")
            .setMessage("Are you sure you want to delete '${task.title}'?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    db.taskDao().deleteTask(task)
                    AutoBackupHelper.triggerSilentBackup(requireContext())
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Task deleted", Toast.LENGTH_SHORT).show()
                        loadTasks()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    inner class ParentTaskAdapter(private var tasks: List<Task>) : RecyclerView.Adapter<ParentTaskAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tvTaskTitle)
            val tvTime: TextView = view.findViewById(R.id.tvTaskTime)
            val tvStatus: TextView = view.findViewById(R.id.tvTaskStatus)
            val btnEdit: Button = view.findViewById(R.id.btnEditTask)
            val btnDelete: Button = view.findViewById(R.id.btnDeleteTask)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_task_parent, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val task = tasks[position]
            holder.tvTitle.text = task.title

            holder.tvTime.text = if (task.description.isNotEmpty()) task.description else "No description"

            // TIMESTAMP LOGIC
            val timeStr = if (task.completionTimeMillis > 0L) {
                SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(task.completionTimeMillis))
            } else ""

            holder.tvStatus.text = when (task.status) {
                "DONE" -> "DONE ($timeStr)"
                else -> task.status
            }

            holder.tvStatus.setTextColor(
                when (task.status) {
                    "DONE" -> 0xFF4CAF50.toInt()
                    else -> 0xFFFF9800.toInt()
                }
            )

            holder.btnEdit.setOnClickListener { showTaskOptions(task) }
            holder.btnDelete.setOnClickListener { showDeleteConfirmation(task) }

            holder.itemView.setOnLongClickListener {
                showTaskOptions(task)
                true
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