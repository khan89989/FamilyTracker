package com.family.tracker.fragments

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.family.tracker.R
import com.family.tracker.data.AppDatabase
import com.family.tracker.data.PrayerLog
import com.family.tracker.databinding.FragmentPrayerHistoryBinding
import com.family.tracker.utils.AutoBackupHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class PrayerHistoryFragment : Fragment() {

    private var _binding: FragmentPrayerHistoryBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: AppDatabase
    private var childId: Long = -1
    private lateinit var adapter: PrayerHistoryAdapter

    private var currentWeekStart: Calendar = Calendar.getInstance().apply {
        firstDayOfWeek = Calendar.MONDAY
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPrayerHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        childId = arguments?.getLong("CHILD_ID") ?: arguments?.getLong("USER_ID") ?: -1
        db = AppDatabase.getDatabase(requireContext())

        adapter = PrayerHistoryAdapter(emptyList())
        binding.rvPrayerHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPrayerHistory.adapter = adapter

        binding.btnPrevWeek.setOnClickListener {
            currentWeekStart.add(Calendar.DAY_OF_YEAR, -7)
            loadHistory()
        }

        binding.btnNextWeek.setOnClickListener {
            currentWeekStart.add(Calendar.DAY_OF_YEAR, 7)
            loadHistory()
        }

        loadHistory()
    }

    override fun onResume() {
        super.onResume()
        loadHistory()
    }

    private fun loadHistory() {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val startDateStr = dateFormat.format(currentWeekStart.time)

        val endCal = currentWeekStart.clone() as Calendar
        endCal.add(Calendar.DAY_OF_YEAR, 6)
        val endDateStr = dateFormat.format(endCal.time)

        val displayFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
        binding.tvDateRange.text = "(${displayFormat.format(currentWeekStart.time)} - ${displayFormat.format(endCal.time)})"
        binding.tvWeekNumber.text = "Week ${currentWeekStart.get(Calendar.WEEK_OF_YEAR)}"

        lifecycleScope.launch(Dispatchers.IO) {
            val logs = db.prayerDao().getLogsForDateRange(childId, startDateStr, endDateStr)
            withContext(Dispatchers.Main) {
                adapter.updateData(logs)
            }
        }
    }

    private fun showResetPrayerDialog(log: PrayerLog) {
        AlertDialog.Builder(requireContext())
            .setTitle("Reset Prayer")
            .setMessage("Reset ${log.prayerName} on ${log.date} to PENDING?")
            .setPositiveButton("Yes") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    db.prayerDao().insertOrUpdateLog(log.copy(status = "PENDING", reason = ""))
                    AutoBackupHelper.triggerSilentBackup(requireContext())
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Prayer reset to PENDING", Toast.LENGTH_SHORT).show()
                        loadHistory()
                    }
                }
            }
            .setNegativeButton("No", null)
            .show()
    }

    inner class PrayerHistoryAdapter(private var logs: List<PrayerLog>) : RecyclerView.Adapter<PrayerHistoryAdapter.ViewHolder>() {

        private val prayers = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvHistoryPrayerName: TextView = view.findViewById(R.id.tvHistoryPrayerName)
            val tvMon: TextView = view.findViewById(R.id.tvMon)
            val tvTue: TextView = view.findViewById(R.id.tvTue)
            val tvWed: TextView = view.findViewById(R.id.tvWed)
            val tvThu: TextView = view.findViewById(R.id.tvThu)
            val tvFri: TextView = view.findViewById(R.id.tvFri)
            val tvSat: TextView = view.findViewById(R.id.tvSat)
            val tvSun: TextView = view.findViewById(R.id.tvSun)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_prayer_history_grid, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val prayerName = prayers[position]
            holder.tvHistoryPrayerName.text = prayerName

            val dayViews = listOf(holder.tvMon, holder.tvTue, holder.tvWed, holder.tvThu, holder.tvFri, holder.tvSat, holder.tvSun)
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            for (i in 0..6) {
                val dayCal = currentWeekStart.clone() as Calendar
                dayCal.add(Calendar.DAY_OF_YEAR, i)
                val dayDateStr = dateFormat.format(dayCal.time)

                val log = logs.find { it.date == dayDateStr && it.prayerName == prayerName }
                val textView = dayViews[i]

                when (log?.status) {
                    "DONE" -> {
                        textView.text = "✔"
                        textView.setTextColor(Color.parseColor("#4CAF50"))
                    }
                    "MISSED" -> {
                        textView.text = "✖"
                        textView.setTextColor(Color.parseColor("#F44336"))
                    }
                    "PENDING" -> {
                        textView.text = "⏳"
                        textView.setTextColor(Color.parseColor("#FF9800"))
                    }
                    else -> {
                        textView.text = "-"
                        textView.setTextColor(Color.parseColor("#888888"))
                    }
                }

                // FIXED: Add click listener to reset if the log exists
                textView.setOnClickListener {
                    if (log != null) {
                        showResetPrayerDialog(log)
                    }
                }
            }
        }

        override fun getItemCount() = prayers.size

        fun updateData(newLogs: List<PrayerLog>) {
            logs = newLogs
            notifyDataSetChanged()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}