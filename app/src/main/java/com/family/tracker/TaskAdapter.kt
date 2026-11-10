package com.family.tracker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.family.tracker.data.Task
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class TaskAdapter(
    private var tasks: List<Task>,
    private val isParent: Boolean,
    private val onTaskToggled: (Task) -> Unit,
    private val onTaskClicked: (Task) -> Unit = {}
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    class TaskViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cbDone: CheckBox = view.findViewById(R.id.cbTaskDone)
        val tvTitle: TextView = view.findViewById(R.id.tvTaskTitle)
        val tvDescription: TextView = view.findViewById(R.id.tvTaskDescription)
        val tvStatus: TextView = view.findViewById(R.id.tvTaskStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = tasks[position]
        val now = System.currentTimeMillis()

        holder.tvTitle.text = task.title
        holder.tvDescription.text = if (task.description.isEmpty()) "No description" else task.description

        if (isParent) {
            holder.cbDone.visibility = View.GONE
        } else {
            holder.cbDone.visibility = View.VISIBLE
            holder.cbDone.isChecked = task.status == "DONE"
        }

        if (task.status == "DONE") {
            val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val completedTime = timeFormat.format(Date(task.completionTimeMillis))
            holder.tvStatus.text = "Completed at $completedTime"
            holder.tvStatus.setTextColor(0xFF4CAF50.toInt())
        } else if (now < task.startTimeMillis) {
            val diff = task.startTimeMillis - now
            val hours = TimeUnit.MILLISECONDS.toHours(diff)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(diff) % 60
            holder.tvStatus.text = String.format("Upcoming: %02d:%02d remaining", hours, minutes)
            holder.tvStatus.setTextColor(0xFF2196F3.toInt())
        } else if (now in task.startTimeMillis until task.endTimeMillis) {
            val diff = task.endTimeMillis - now
            val hours = TimeUnit.MILLISECONDS.toHours(diff)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(diff) % 60
            holder.tvStatus.text = String.format("In Progress: %02d:%02d remaining", hours, minutes)
            holder.tvStatus.setTextColor(0xFFFF9800.toInt())
        } else {
            val diff = now - task.endTimeMillis
            val hours = TimeUnit.MILLISECONDS.toHours(diff)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(diff) % 60
            holder.tvStatus.text = String.format("Overdue by %02d:%02d", hours, minutes)
            holder.tvStatus.setTextColor(0xFFF44336.toInt())
        }

        holder.itemView.setOnClickListener {
            if (isParent) {
                onTaskClicked(task)
            } else {
                onTaskToggled(task)
            }
        }
    }

    override fun getItemCount() = tasks.size

    fun updateData(newTasks: List<Task>) {
        tasks = newTasks
        notifyDataSetChanged()
    }
}