package com.family.tracker.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.family.tracker.R
import com.family.tracker.data.AppDatabase
import com.family.tracker.data.Habit
import com.family.tracker.data.HabitLog
import com.family.tracker.databinding.FragmentHabitHistoryBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HabitHistoryFragment : Fragment() {

    private var _binding: FragmentHabitHistoryBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: AppDatabase
    private var habitId: Long = -1
    private var habit: Habit? = null
    private var logsMap: Map<String, HabitLog> = emptyMap()
    private lateinit var adapter: MonthsAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHabitHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        habitId = arguments?.getLong("HABIT_ID") ?: -1
        db = AppDatabase.getDatabase(requireContext())

        adapter = MonthsAdapter(emptyList())
        binding.rvHabitHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHabitHistory.adapter = adapter

        loadHistory()
    }

    private fun loadHistory() {
        lifecycleScope.launch(Dispatchers.IO) {
            habit = db.habitDao().getHabitById(habitId)
            val logs = db.habitDao().getLogsForHabit(habitId)
            logsMap = logs.associateBy { it.date }

            // Generate months list (12 months in the past to 12 months in the future)
            val months = mutableListOf<Calendar>()
            val cal = Calendar.getInstance().apply {
                firstDayOfWeek = Calendar.MONDAY
                set(Calendar.DAY_OF_MONTH, 1)
                add(Calendar.MONTH, -12)
            }
            repeat(25) {
                months.add(cal.clone() as Calendar)
                cal.add(Calendar.MONTH, 1)
            }

            withContext(Dispatchers.Main) {
                adapter.updateData(months)
                binding.rvHabitHistory.scrollToPosition(12)
            }
        }
    }

    private fun isDueOn(h: Habit?, dateStr: String): Boolean {
        if (h == null) return false
        if (h.startDate.isNotEmpty() && dateStr < h.startDate) return false
        if (h.optionalEndDate.isNotEmpty() && dateStr > h.optionalEndDate) return false

        if (h.repeatType == "DAILY") return true
        val selected = h.repeatDays.split(",").mapNotNull { it.toIntOrNull() }.toSet()
        if (selected.isEmpty()) return true
        val cal = Calendar.getInstance()
        try {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr)?.let { cal.time = it }
        } catch (_: Exception) { return true }
        return when (h.repeatType) {
            "WEEKLY" -> {
                val dow = cal.get(Calendar.DAY_OF_WEEK)
                val isoDow = if (dow == Calendar.SUNDAY) 7 else dow - 1
                isoDow in selected
            }
            "MONTHLY" -> cal.get(Calendar.DAY_OF_MONTH) in selected
            else -> true
        }
    }

    inner class MonthsAdapter(private var months: List<Calendar>) : RecyclerView.Adapter<MonthsAdapter.MonthViewHolder>() {

        inner class MonthViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvMonthTitle: TextView = view.findViewById(R.id.tvMonthTitle)
            val rvMonthDaysGrid: RecyclerView = view.findViewById(R.id.rvMonthDaysGrid)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MonthViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_habit_history_month, parent, false)
            return MonthViewHolder(v)
        }

        override fun onBindViewHolder(holder: MonthViewHolder, position: Int) {
            val monthCal = months[position]
            val titleFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
            holder.tvMonthTitle.text = titleFormat.format(monthCal.time)

            val daysAdapter = DaysAdapter(monthCal)
            holder.rvMonthDaysGrid.layoutManager = GridLayoutManager(holder.itemView.context, 7)
            holder.rvMonthDaysGrid.adapter = daysAdapter
        }

        override fun getItemCount() = months.size

        fun updateData(newMonths: List<Calendar>) {
            months = newMonths
            notifyDataSetChanged()
        }
    }

    inner class DaysAdapter(monthCal: Calendar) : RecyclerView.Adapter<DaysAdapter.DayViewHolder>() {

        private val daysList: List<DateCell> = generateDaysForMonth(monthCal)

        inner class DayViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvDayNumber: TextView = view.findViewById(R.id.tvDayNumber)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_habit_calendar_day, parent, false)
            return DayViewHolder(v)
        }

        override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
            val cell = daysList[position]
            if (cell.dayNumber == 0) {
                holder.tvDayNumber.text = ""
                holder.tvDayNumber.setBackgroundColor(Color.TRANSPARENT)
                return
            }

            holder.tvDayNumber.text = cell.dayNumber.toString()
            val dateStr = cell.dateStr
            val isDue = isDueOn(habit, dateStr)
            val log = logsMap[dateStr]
            val status = log?.status ?: "PENDING"

            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().time)
            val isPastOrToday = dateStr <= todayStr
            val isToday = (dateStr == todayStr)

            if (isToday) {
                holder.tvDayNumber.setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                holder.tvDayNumber.setTypeface(null, android.graphics.Typeface.NORMAL)
            }

            when {
                !isDue -> {
                    // White for non habit
                    holder.tvDayNumber.setBackgroundColor(Color.parseColor("#FFFFFF"))
                    holder.tvDayNumber.setTextColor(Color.parseColor("#888888"))
                }
                status == "DONE" -> {
                    // Green for done habits
                    holder.tvDayNumber.setBackgroundColor(Color.parseColor("#4CAF50"))
                    holder.tvDayNumber.setTextColor(Color.parseColor("#FFFFFF"))
                }
                status == "SKIPPED" || (isPastOrToday && status == "PENDING") -> {
                    // Red for skipped & undone habits
                    holder.tvDayNumber.setBackgroundColor(Color.parseColor("#FFEBEE"))
                    holder.tvDayNumber.setTextColor(Color.parseColor("#C62828"))
                }
                else -> {
                    // Light green for habit days (future pending)
                    holder.tvDayNumber.setBackgroundColor(Color.parseColor("#E8F5E9"))
                    holder.tvDayNumber.setTextColor(Color.parseColor("#2E7D32"))
                }
            }
        }

        override fun getItemCount() = daysList.size
    }

    data class DateCell(val dayNumber: Int, val dateStr: String)

    private fun generateDaysForMonth(monthCal: Calendar): List<DateCell> {
        val list = mutableListOf<DateCell>()
        val cal = monthCal.clone() as Calendar
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        cal.set(Calendar.DAY_OF_MONTH, 1)
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val isoDow = if (dow == Calendar.SUNDAY) 7 else dow - 1

        for (i in 1 until isoDow) {
            list.add(DateCell(0, ""))
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        for (day in 1..maxDays) {
            cal.set(Calendar.DAY_OF_MONTH, day)
            list.add(DateCell(day, dateFormat.format(cal.time)))
        }

        return list
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
