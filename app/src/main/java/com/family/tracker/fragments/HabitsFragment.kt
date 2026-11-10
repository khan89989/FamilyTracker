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
import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import com.family.tracker.R
import com.family.tracker.data.AppDatabase
import com.family.tracker.data.AppSettings
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

class HabitsFragment : Fragment() {

    private var _binding: FragmentHabitsBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: AppDatabase
    private var childId: Long = -1
    private var dateString: String = ""
    private lateinit var adapter: HabitAdapter
    private var dbSettings: AppSettings? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHabitsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        childId = arguments?.getLong("CHILD_ID") ?: arguments?.getLong("USER_ID") ?: -1
        dateString = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
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
            dbSettings = db.settingsDao().getSettings() ?: AppSettings()
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

        val repeatType = habit.repeatType.uppercase()
        if (repeatType == "DAILY") return true

        val cal = Calendar.getInstance()
        try {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr)?.let { cal.time = it }
        } catch (_: Exception) { return false }

        val repeatDays = habit.repeatDays.uppercase()
        val parts = repeatDays.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        return when (repeatType) {
            "WEEKLY" -> {
                val dow = cal.get(Calendar.DAY_OF_WEEK)
                val isoDow = if (dow == Calendar.SUNDAY) 7 else dow - 1
                val dayNameShort = SimpleDateFormat("EEE", Locale.ENGLISH).format(cal.time).uppercase()
                val dayNameLong = SimpleDateFormat("EEEE", Locale.ENGLISH).format(cal.time).uppercase()

                parts.any { p ->
                    p == isoDow.toString() || p == dayNameShort || p == dayNameLong || dayNameShort.startsWith(p)
                }
            }
            "MONTHLY" -> {
                val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
                parts.any { it.toIntOrNull() == dayOfMonth }
            }
            else -> true
        }
    }

    private fun calculateHabitTime(habit: Habit, dateStr: String): String {
        if (habit.prayerReference == "None") return ""
        val settings = dbSettings ?: AppSettings()
        val method = when (settings.methodId) {
            "EGYPTIAN" -> CalculationMethod.EGYPTIAN
            "KARACHI" -> CalculationMethod.KARACHI
            "UMM_AL_QURA" -> CalculationMethod.UMM_AL_QURA
            "NORTH_AMERICA" -> CalculationMethod.NORTH_AMERICA
            "DUBAI" -> CalculationMethod.DUBAI
            "QATAR" -> CalculationMethod.QATAR
            "KUWAIT" -> CalculationMethod.KUWAIT
            "SINGAPORE" -> CalculationMethod.SINGAPORE
            else -> CalculationMethod.MUSLIM_WORLD_LEAGUE
        }
        val targetDate: Date = try {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr) ?: Date()
        } catch (_: Exception) { Date() }
        val coordinates = Coordinates(settings.latitude, settings.longitude)
        val dateComponents = DateComponents.from(targetDate)
        val params = method.parameters
        params.madhab = Madhab.SHAFI
        val times = PrayerTimes(coordinates, dateComponents, params)

        val baseTime = when (habit.prayerReference) {
            "Fajr" -> times.fajr.time
            "Dhuhr" -> times.dhuhr.time
            "Asr" -> times.asr.time
            "Maghrib" -> times.maghrib.time
            "Isha" -> times.isha.time
            else -> return ""
        }
        val adjusted = baseTime + habit.offsetMinutes * 60 * 1000L
        return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(adjusted))
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

            val absTime = calculateHabitTime(habit, dateString)
            val timePart = if (absTime.isNotEmpty()) "Time: $absTime" else ""
            val repeatPart = when (habit.repeatType.uppercase()) {
                "DAILY" -> "(DAILY)"
                "WEEKLY" -> {
                    val dayNames = mapOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun")
                    val days = habit.repeatDays.split(",")
                        .mapNotNull { it.trim().toIntOrNull() }
                        .mapNotNull { dayNames[it] }
                        .joinToString(", ")
                    if (days.isNotEmpty()) "[$days]" else ""
                }
                "MONTHLY" -> {
                    val days = habit.repeatDays.split(",")
                        .mapNotNull { it.trim().toIntOrNull() }
                        .joinToString(", ")
                    if (days.isNotEmpty()) "[$days]" else ""
                }
                else -> habit.repeatType
            }
            val timeStr = if (log?.completionTimeMillis ?: 0L > 0) {
                ", Done: ${SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(log!!.completionTimeMillis))}"
            } else ""

            holder.tvHabitDetails.text = if (timePart.isNotEmpty()) "$timePart, $repeatPart$timeStr" else "$repeatPart$timeStr"

            holder.cbHabitDone.setOnCheckedChangeListener(null)
            holder.cbHabitDone.isChecked = (status == "DONE")

            holder.cbHabitDone.setOnClickListener {
                val isChecked = holder.cbHabitDone.isChecked
                val newStatus = if (isChecked) "DONE" else "PENDING"

                holder.cbHabitDone.isChecked = !isChecked
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
