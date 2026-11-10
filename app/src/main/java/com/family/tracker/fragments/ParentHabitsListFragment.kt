package com.family.tracker.fragments

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import com.family.tracker.HabitHistoryActivity
import com.family.tracker.R
import com.family.tracker.data.AppDatabase
import com.family.tracker.data.AppSettings
import com.family.tracker.data.Habit
import com.family.tracker.data.HabitLog
import com.family.tracker.databinding.DialogAddHabitBinding
import com.family.tracker.databinding.FragmentParentHabitsBinding
import com.family.tracker.utils.AutoBackupHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ParentHabitsListFragment : Fragment() {
    private var _binding: FragmentParentHabitsBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: AppDatabase
    private var childId: Long = -1
    private var dateString: String = ""
    private lateinit var adapter: ParentHabitAdapter
    private var dbSettings: AppSettings? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentParentHabitsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        childId = arguments?.getLong("CHILD_ID") ?: arguments?.getLong("USER_ID") ?: -1
        dateString = arguments?.getString("DATE_STRING") ?: todayDate()
        db = AppDatabase.getDatabase(requireContext())

        adapter = ParentHabitAdapter(emptyList())
        binding.rvParentHabits.layoutManager = LinearLayoutManager(requireContext())
        binding.rvParentHabits.adapter = adapter

        loadHabits()
        binding.btnAddHabit.setOnClickListener { showHabitDialog(null) }
    }

    override fun onResume() {
        super.onResume()
        loadHabits()
    }

    private fun todayDate(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    private fun loadHabits() {
        lifecycleScope.launch(Dispatchers.IO) {
            dbSettings = db.settingsDao().getSettings() ?: AppSettings()
            val habits = db.habitDao().getHabitsForChild(childId)
            val allLogs = db.habitDao().getAllHabitLogs().groupBy { it.habitId }

            val todaysStatus = habits.mapNotNull { h ->
                allLogs[h.id]?.firstOrNull { it.date == dateString }?.let { h.id to it }
            }.toMap()

            val notDue = habits.filter { !isDueOn(it, dateString) }.map { it.id }.toSet()

            withContext(Dispatchers.Main) { adapter.updateData(habits, todaysStatus, notDue) }
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

    private fun showHabitOptions(habit: Habit, currentStatus: String) {
        val options = mutableListOf("Edit Habit", "Delete")
        if (currentStatus != "PENDING") options.add("Reset Status")

        AlertDialog.Builder(requireContext())
            .setTitle("Manage Habit")
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Edit Habit" -> showHabitDialog(habit)
                    "Delete" -> deleteHabit(habit)
                    "Reset Status" -> resetHabit(habit)
                }
            }
            .show()
    }

    private fun deleteHabit(habit: Habit) {
        lifecycleScope.launch(Dispatchers.IO) {
            db.habitDao().deleteLogsForHabit(habit.id)
            db.habitDao().deleteHabitById(habit.id)
            AutoBackupHelper.triggerSilentBackup(requireContext())
            withContext(Dispatchers.Main) { loadHabits() }
        }
    }

    private fun resetHabit(habit: Habit) {
        lifecycleScope.launch(Dispatchers.IO) {
            val log = db.habitDao().getLogForDate(habit.id, dateString)
            if (log != null) db.habitDao().updateLogStatus(log.id, "PENDING", 0L)
            AutoBackupHelper.triggerSilentBackup(requireContext())
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "Habit reset to PENDING", Toast.LENGTH_SHORT).show()
                loadHabits()
            }
        }
    }

    private fun showHabitDialog(existingHabit: Habit?) {
        val b = DialogAddHabitBinding.inflate(layoutInflater)

        val prayerOptions = arrayOf("None", "Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")
        b.spinnerPrayerRef.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, prayerOptions
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        b.npDirection.minValue = 0
        b.npDirection.maxValue = 1
        b.npDirection.displayedValues = arrayOf("After", "Before")
        b.npHours.minValue = 0; b.npHours.maxValue = 23
        b.npMinutes.minValue = 0; b.npMinutes.maxValue = 59

        val initialWeekly = mutableSetOf<Int>()
        val initialMonthly = mutableSetOf<Int>()

        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        var startDateStr = existingHabit?.startDate?.takeIf { it.isNotEmpty() } ?: todayStr
        var endDateStr = existingHabit?.optionalEndDate ?: ""

        b.btnStartDate.text = "Start: $startDateStr"
        b.btnEndDate.text = if (endDateStr.isNotEmpty()) "End: $endDateStr" else "Select End Date (Optional)"

        b.btnStartDate.setOnClickListener {
            val cal = Calendar.getInstance()
            try {
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(startDateStr)?.let { cal.time = it }
            } catch (_: Exception) {}
            android.app.DatePickerDialog(requireContext(), { _, year, month, day ->
                val c = Calendar.getInstance().apply { set(year, month, day) }
                startDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(c.time)
                b.btnStartDate.text = "Start: $startDateStr"
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        b.btnEndDate.setOnClickListener {
            val cal = Calendar.getInstance()
            if (endDateStr.isNotEmpty()) {
                try {
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(endDateStr)?.let { cal.time = it }
                } catch (_: Exception) {}
            }
            val datePicker = android.app.DatePickerDialog(requireContext(), { _, year, month, day ->
                val c = Calendar.getInstance().apply { set(year, month, day) }
                endDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(c.time)
                b.btnEndDate.text = "End: $endDateStr"
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))

            datePicker.setButton(android.content.DialogInterface.BUTTON_NEUTRAL, "Clear") { _, _ ->
                endDateStr = ""
                b.btnEndDate.text = "Select End Date (Optional)"
            }
            datePicker.show()
        }

        if (existingHabit != null) {
            b.etHabitTitle.setText(existingHabit.title)
            when (existingHabit.repeatType) {
                "WEEKLY" -> b.rbWeekly.isChecked = true
                "MONTHLY" -> b.rbMonthly.isChecked = true
                else -> b.rbDaily.isChecked = true
            }
            val sel = existingHabit.repeatDays.split(",")
                .mapNotNull { it.toIntOrNull() }.toSet()
            if (existingHabit.repeatType == "WEEKLY") initialWeekly.addAll(sel)
            if (existingHabit.repeatType == "MONTHLY") initialMonthly.addAll(sel)

            val isBefore = existingHabit.offsetMinutes < 0
            b.npDirection.value = if (isBefore) 1 else 0
            val abs = Math.abs(existingHabit.offsetMinutes)
            b.npHours.value = abs / 60
            b.npMinutes.value = abs % 60
            val pIdx = prayerOptions.indexOf(existingHabit.prayerReference)
            if (pIdx != -1) b.spinnerPrayerRef.setSelection(pIdx)
        }

        val typedValue = TypedValue()
        requireContext().theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
        val defaultColor = if (typedValue.resourceId != 0) {
            ContextCompat.getColor(requireContext(), typedValue.resourceId)
        } else {
            typedValue.data
        }

        requireContext().theme.resolveAttribute(androidx.appcompat.R.attr.colorAccent, typedValue, true)
        val selectedColor = if (typedValue.resourceId != 0) {
            ContextCompat.getColor(requireContext(), typedValue.resourceId)
        } else {
            typedValue.data
        }

        buildWeeklyPicker(b.layoutWeeklyDays, initialWeekly, defaultColor, selectedColor)
        buildMonthlyPicker(b.layoutMonthlyDays, initialMonthly, defaultColor, selectedColor)

        fun refreshPickerVisibility() {
            b.layoutWeeklyDays.visibility = if (b.rbWeekly.isChecked) View.VISIBLE else View.GONE
            b.layoutMonthlyDays.visibility = if (b.rbMonthly.isChecked) View.VISIBLE else View.GONE
        }
        b.rgRepeat.setOnCheckedChangeListener { _: RadioGroup, _: Int -> refreshPickerVisibility() }
        refreshPickerVisibility()

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(if (existingHabit == null) "Add Habit" else "Edit Habit")
            .setView(b.root)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = b.etHabitTitle.text.toString().trim()
                val ref = b.spinnerPrayerRef.selectedItem.toString()
                val repeat = when {
                    b.rbWeekly.isChecked -> "WEEKLY"
                    b.rbMonthly.isChecked -> "MONTHLY"
                    else -> "DAILY"
                }
                val isBefore = b.npDirection.value == 1
                var offset = b.npHours.value * 60 + b.npMinutes.value
                if (isBefore) offset *= -1

                if (title.isEmpty()) {
                    b.etHabitTitle.error = "Title is required"
                    return@setOnClickListener
                }
                if (startDateStr.isEmpty()) {
                    Toast.makeText(requireContext(), "Start date is mandatory", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (endDateStr.isNotEmpty() && endDateStr < startDateStr) {
                    Toast.makeText(requireContext(), "End date cannot be before start date", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val selectedDays: List<Int> = when (repeat) {
                    "WEEKLY" -> collectWeekly(b.layoutWeeklyDays)
                    "MONTHLY" -> collectMonthly(b.layoutMonthlyDays)
                    else -> emptyList()
                }
                if (repeat != "DAILY" && selectedDays.isEmpty()) {
                    Toast.makeText(requireContext(),
                        "Pick at least one ${if (repeat == "WEEKLY") "day" else "date"}",
                        Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val repeatDays = selectedDays.joinToString(",")

                lifecycleScope.launch(Dispatchers.IO) {
                    if (existingHabit == null) {
                        db.habitDao().insertHabit(
                            Habit(childId = childId, title = title, prayerReference = ref,
                                offsetMinutes = offset, repeatType = repeat, repeatDays = repeatDays,
                                startDate = startDateStr, optionalEndDate = endDateStr)
                        )
                    } else {
                        db.habitDao().updateHabit(
                            existingHabit.copy(title = title, prayerReference = ref,
                                offsetMinutes = offset, repeatType = repeat, repeatDays = repeatDays,
                                startDate = startDateStr, optionalEndDate = endDateStr)
                        )
                    }
                    AutoBackupHelper.triggerSilentBackup(requireContext())
                    withContext(Dispatchers.Main) {
                        dialog.dismiss()
                        loadHabits()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun buildWeeklyPicker(container: LinearLayout, selected: Set<Int>, defaultColor: Int, selectedColor: Int) {
        container.removeAllViews()
        val labels = listOf(1 to "M", 2 to "T", 3 to "W", 4 to "T", 5 to "F", 6 to "S", 7 to "S")
        labels.forEach { (num, lbl) ->
            val tv = TextView(requireContext()).apply {
                text = lbl
                tag = num
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(0, 16, 0, 16)
                isSelected = selected.contains(num)
                setTextColor(if (isSelected) selectedColor else defaultColor)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    isSelected = !isSelected
                    setTextColor(if (isSelected) selectedColor else defaultColor)
                }
            }
            container.addView(tv)
        }
    }

    private fun buildMonthlyPicker(container: LinearLayout, selected: Set<Int>, defaultColor: Int, selectedColor: Int) {
        container.removeAllViews()
        var currentRow: LinearLayout? = null
        for (day in 1..31) {
            if ((day - 1) % 7 == 0) {
                currentRow = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                container.addView(currentRow)
            }
            val tv = TextView(requireContext()).apply {
                text = day.toString()
                tag = day
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(0, 16, 0, 16)
                isSelected = selected.contains(day)
                setTextColor(if (isSelected) selectedColor else defaultColor)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    isSelected = !isSelected
                    setTextColor(if (isSelected) selectedColor else defaultColor)
                }
            }
            currentRow?.addView(tv)
        }
    }

    private fun collectWeekly(container: LinearLayout): List<Int> =
        (0 until container.childCount)
            .map { container.getChildAt(it) as TextView }
            .filter { it.isSelected }
            .mapNotNull { it.tag as? Int }
            .sorted()

    private fun collectMonthly(container: LinearLayout): List<Int> =
        (0 until container.childCount)
            .flatMap { rowIndex ->
                val row = container.getChildAt(rowIndex) as? LinearLayout
                (0 until (row?.childCount ?: 0)).map { colIndex -> row?.getChildAt(colIndex) as? TextView }
            }
            .filterNotNull()
            .filter { it.isSelected }
            .mapNotNull { it.tag as? Int }
            .sorted()

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

    inner class ParentHabitAdapter(
        private var habits: List<Habit>,
        private var logs: Map<Long, HabitLog> = emptyMap(),
        private var notDue: Set<Long> = emptySet()
    ) : RecyclerView.Adapter<ParentHabitAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tvHabitTitle)
            val tvSchedule: TextView = view.findViewById(R.id.tvHabitSchedule)
            val tvStatus: TextView = view.findViewById(R.id.tvHabitStatus)
            val btnHistory: Button = view.findViewById(R.id.btnHistory) // FIXED: Bound here
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_habit_parent, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val habit = habits[position]
            val log = logs[habit.id]
            val status = log?.status ?: "PENDING"

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
            val scheduleText = if (timePart.isNotEmpty()) "$timePart, $repeatPart" else repeatPart
            val dueNote = if (notDue.contains(habit.id)) "  •  Not scheduled today" else ""

            holder.tvTitle.text = "${habit.title}$dueNote"
            holder.tvSchedule.text = scheduleText

            val timeStr = if (log?.completionTimeMillis ?: 0L > 0) {
                " (${SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(log!!.completionTimeMillis))})"
            } else ""

            holder.tvStatus.text = "$status$timeStr"
            holder.tvStatus.setTextColor(
                when (status) {
                    "DONE" -> 0xFF4CAF50.toInt()
                    "SKIPPED" -> 0xFF2196F3.toInt()
                    else -> 0xFFFF9800.toInt()
                }
            )

            holder.itemView.setOnClickListener { showHabitOptions(habit, status) }

            holder.btnHistory.setOnClickListener {
                val intent = Intent(requireContext(), HabitHistoryActivity::class.java)
                intent.putExtra("HABIT_ID", habit.id)
                intent.putExtra("HABIT_TITLE", habit.title)
                startActivity(intent)
            }
        }

        override fun getItemCount() = habits.size

        fun updateData(nh: List<Habit>, nl: Map<Long, HabitLog>, nd: Set<Long>) {
            habits = nh; logs = nl; notDue = nd; notifyDataSetChanged()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}