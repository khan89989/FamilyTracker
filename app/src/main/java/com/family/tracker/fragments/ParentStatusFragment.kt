package com.family.tracker.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.family.tracker.R
import com.family.tracker.data.AppDatabase
import com.family.tracker.databinding.FragmentParentStatusBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ParentStatusFragment : Fragment() {

    private var _binding: FragmentParentStatusBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: AppDatabase
    private var childId: Long = -1
    private var dateString: String = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentParentStatusBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        childId = arguments?.getLong("CHILD_ID") ?: -1
        dateString = arguments?.getString("DATE_STRING") ?: ""
        db = AppDatabase.getDatabase(requireContext())

        loadStatus()
    }

    override fun onResume() {
        super.onResume()
        loadStatus()
    }

    private fun loadStatus() {
        lifecycleScope.launch(Dispatchers.IO) {
            // 1. Tasks
            val tasks = db.taskDao().getTasksForChildByDate(childId, dateString)
            val tasksDone = tasks.count { it.status == "DONE" }

            // 2. Habits
            val habits = db.habitDao().getHabitsForChild(childId)
            var habitsDone = 0
            for (h in habits) {
                if (db.habitDao().getLogForDate(h.id, dateString)?.status == "DONE") habitsDone++
            }

            // 3. Homework
            val homework = db.homeworkDao().getHomeworkForChildByDate(childId, dateString)
            val hwDone = homework.count { it.status == "DONE" }

            // 4. Prayers
            val prayers = db.prayerDao().getLogsForDate(childId, dateString)
            val prayersDone = prayers.count { it.status == "DONE" }

            withContext(Dispatchers.Main) {
                binding.tvTasksStatus.text = "$tasksDone / ${tasks.size} Completed"
                binding.tvHabitsStatus.text = "$habitsDone / ${habits.size} Completed"
                binding.tvHomeworkStatus.text = "$hwDone / ${homework.size} Completed"
                binding.tvPrayersStatus.text = "$prayersDone / 5 Completed"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}