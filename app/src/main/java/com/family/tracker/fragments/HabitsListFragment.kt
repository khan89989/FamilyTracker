package com.family.tracker.fragments

import android.app.AlertDialog
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
import com.family.tracker.data.Habit
import com.family.tracker.data.HabitLog
import com.family.tracker.databinding.FragmentHabitsBinding
import com.family.tracker.utils.AutoBackupHelper
import com.family.tracker.utils.PasswordUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HabitsListFragment : Fragment() {

    private var _binding: FragmentHabitsBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: AppDatabase
    private var childId: Long = -1
    private var dateString: String = ""
    private lateinit var adapter: HabitAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHabitsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        childId = arguments?.getLong("CHILD_ID") ?: arguments?.getLong("USER_ID") ?: -1
        dateString = arguments?.getString("DATE_STRING") ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        db = AppDatabase.getDatabase(requireContext())

        adapter = HabitAdapter(emptyList(), emptyMap())
        binding.rvHabits.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHabits.adapter = adapter

        loadHabits()
    }

    override fun onResume() {
        super.onResume()
        loadHabits()
    }

    private fun loadHabits() {
        lifecycleScope.launch(Dispatchers.IO) {
            val habits = db.habitDao().getHabitsForChild(childId)
            val allLogs = db.habitDao().getAllHabitLogs().groupBy { it.habitId }

            val todaysLogs = habits.mapNotNull { h ->
                allLogs[h.id]?.firstOrNull { it.date == dateString }?.let { h.id to it }
            }.toMap()

            val dueHabits = habits.filter { isDueOn(it, dateString) }

            withContext(Dispatchers.Main) {
                adapter.updateData(dueHabits, todaysLogs)
            }
        }
    }

    private fun isDueOn(habit: Habit, dateStr: String): Boolean {
        if (habit.startDate.isNotEmpty() && dateStr < habit.startDate) return false
        if (habit.optionalEndDate.isNotEmpty() && dateStr > habit.optionalEndDate) return false

        if (habit.repeatType == "DAILY") return true
        val selected = habit.repeatDays.split(",").mapNotNull { it.toIntOrNull() }.toSet()
        if (selected.isEmpty()) return true
        val cal = Calendar.getInstance()
        try {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr)?.let { cal.time = it }
        } catch (_: Exception) { return true }
        return when (habit.repeatType) {
            "WEEKLY" -> {
                val dow = cal.get(Calendar.DAY_OF_WEEK)
                val isoDow = if (dow == Calendar.SUNDAY) 7 else dow - 1
                isoDow in selected
            }
            "MONTHLY" -> cal.get(Calendar.DAY_OF_MONTH) in selected
            else -> true
        }
    }

    private fun showMotivationalPopup(isSuccess: Boolean) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_motivational, null)
        val tvEmoji = dialogView.findViewById<TextView>(R.id.tvEmoji)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tvMessage)
        if (isSuccess) { tvEmoji.text = "😃"; tvMessage.text = "Great Job!" } else { tvEmoji.text = "🤔"; tvMessage.text = "Don't Worry!" }
        AlertDialog.Builder(requireContext()).setView(dialogView).setPositiveButton("OK") { d, _ -> d.dismiss() }.setCancelable(false).show()
    }

    private fun showParentPasswordDialog(onSuccess: () -> Unit) {
        val container = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 24, 48, 0) }
        val etPassword = EditText(requireContext()).apply { hint = "Parent Password"; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD }
        container.addView(etPassword)

        AlertDialog.Builder(requireContext()).setTitle("Parent Approval Required").setView(container).setPositiveButton("Approve", null).setNegativeButton("Cancel", null).create().apply {
            setOnShowListener {
                getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val inputPass = etPassword.text.toString().trim()
                    lifecycleScope.launch(Dispatchers.IO) {
                        val parents = db.userDao().getAllUsers().filter { it.role == "PARENT" }
                        val isValid = parents.any { PasswordUtils.hashPassword(inputPass) == it.passwordHash }
                        withContext(Dispatchers.Main) {
                            if (isValid) { onSuccess(); dismiss() }
                            else { Toast.makeText(requireContext(), "Incorrect password!", Toast.LENGTH_SHORT).show() }
                        }
                    }
                }
            }
        }.show()
    }

    private fun markHabitStatus(habit: Habit, status: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val existingLog = db.habitDao().getLogForDate(habit.id, dateString)
            val now = System.currentTimeMillis()
            if (existingLog == null) db.habitDao().insertLog(HabitLog(habitId = habit.id, date = dateString, status = status, completionTimeMillis = now))
            else db.habitDao().updateLogStatus(existingLog.id, status, now)

            // FIXED: Changed 1 and -1 to 1.0 and -1.0 for Double
            if (status == "DONE" && existingLog?.status != "DONE") {
                db.userDao().updatePoints(childId, 1.0)
            } else if (status != "DONE" && existingLog?.status == "DONE") {
                db.userDao().updatePoints(childId, -1.0)
            }

            AutoBackupHelper.triggerSilentBackup(requireContext())
            withContext(Dispatchers.Main) {
                loadHabits()
                (activity as? com.family.tracker.ChildActivity)?.refreshPoints()
                if (status == "DONE") showMotivationalPopup(true)
            }
        }
    }

    inner class HabitAdapter(
        private var habits: List<Habit>,
        private var logs: Map<Long, HabitLog>
    ) : RecyclerView.Adapter<HabitAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val cbHabitDone: CheckBox = view.findViewById(R.id.cbHabitDone)
            val tvHabitTitle: TextView = view.findViewById(R.id.tvHabitTitle)
            val tvHabitDetails: TextView = view.findViewById(R.id.tvHabitDetails)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_child_habit, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val habit = habits[position]
            val log = logs[habit.id]
            val status = log?.status ?: "PENDING"

            holder.tvHabitTitle.text = habit.title

            val timeStr = if (log?.completionTimeMillis ?: 0L > 0) {
                " • Done: ${SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(log!!.completionTimeMillis))}"
            } else ""

            holder.tvHabitDetails.text = "Repeat: ${habit.repeatType}$timeStr"

            holder.cbHabitDone.setOnCheckedChangeListener(null)
            holder.cbHabitDone.isChecked = (status == "DONE")

            holder.cbHabitDone.setOnClickListener {
                val isChecked = holder.cbHabitDone.isChecked
                val newStatus = if (isChecked) "DONE" else "PENDING"

                holder.cbHabitDone.isChecked = !isChecked // Revert temporarily
                showParentPasswordDialog { markHabitStatus(habit, newStatus) }
            }

            holder.itemView.setOnClickListener {
                val newStatus = if (status == "DONE") "PENDING" else "DONE"
                showParentPasswordDialog { markHabitStatus(habit, newStatus) }
            }
        }

        override fun getItemCount() = habits.size

        fun updateData(newHabits: List<Habit>, newLogs: Map<Long, HabitLog>) {
            habits = newHabits
            logs = newLogs
            notifyDataSetChanged()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}