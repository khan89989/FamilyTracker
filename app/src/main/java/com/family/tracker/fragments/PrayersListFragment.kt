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
import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import com.family.tracker.R
import com.family.tracker.data.AppDatabase
import com.family.tracker.data.AppSettings
import com.family.tracker.data.PrayerLog
import com.family.tracker.databinding.FragmentPrayersListBinding
import com.family.tracker.utils.AutoBackupHelper
import com.family.tracker.utils.PasswordUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PrayersListFragment : Fragment() {

    private var _binding: FragmentPrayersListBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: AppDatabase
    private var childId: Long = -1
    private var dateString: String = ""
    private lateinit var adapter: PrayerAdapter
    private var dbSettings: AppSettings? = null

    private val prayers = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPrayersListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        childId = arguments?.getLong("CHILD_ID") ?: arguments?.getLong("USER_ID") ?: -1
        dateString = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        db = AppDatabase.getDatabase(requireContext())

        adapter = PrayerAdapter(emptyMap())
        binding.rvPrayers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPrayers.adapter = adapter

        loadPrayers()
    }

    override fun onResume() {
        super.onResume()
        loadPrayers()
    }

    private fun loadPrayers() {
        lifecycleScope.launch(Dispatchers.IO) {
            dbSettings = db.settingsDao().getSettings() ?: AppSettings()
            val logs = db.prayerDao().getLogsForDate(childId, dateString)
            val statusMap = logs.associateBy { it.prayerName }
            withContext(Dispatchers.Main) {
                adapter.updateData(statusMap)
            }
        }
    }

    private fun calculatePrayerTime(prayerName: String): String {
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
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateString) ?: Date()
        } catch (_: Exception) { Date() }
        val coordinates = Coordinates(settings.latitude, settings.longitude)
        val dateComponents = DateComponents.from(targetDate)
        val params = method.parameters
        params.madhab = Madhab.SHAFI
        val times = PrayerTimes(coordinates, dateComponents, params)

        val baseTime = when (prayerName) {
            "Fajr" -> times.fajr.time
            "Dhuhr" -> times.dhuhr.time
            "Asr" -> times.asr.time
            "Maghrib" -> times.maghrib.time
            "Isha" -> times.isha.time
            else -> return ""
        }
        return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(baseTime))
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

    private fun markPrayer(prayerName: String, status: String, reason: String = "") {
        lifecycleScope.launch(Dispatchers.IO) {
            val existing = db.prayerDao().getLogsForDate(childId, dateString).find { it.prayerName == prayerName }
            val log = existing?.copy(status = status, reason = reason)
                ?: PrayerLog(childId = childId, date = dateString, prayerName = prayerName, status = status, reason = reason)

            db.prayerDao().insertOrUpdateLog(log)
            AutoBackupHelper.triggerSilentBackup(requireContext())

            withContext(Dispatchers.Main) {
                loadPrayers()
            }
        }
    }

    private fun showMissedDialog(prayerName: String) {
        val reasons = arrayOf("Forgot", "Slept", "Sick", "Other")
        AlertDialog.Builder(requireContext())
            .setTitle("Why was $prayerName missed?")
            .setItems(reasons) { _, which ->
                showParentPasswordDialog {
                    markPrayer(prayerName, "MISSED", reasons[which])
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    inner class PrayerAdapter(private var logsMap: Map<String, PrayerLog>) : RecyclerView.Adapter<PrayerAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvPrayerName: TextView = view.findViewById(R.id.tvPrayerName)
            val tvPrayerTime: TextView = view.findViewById(R.id.tvPrayerTime)
            val tvPrayerStatus: TextView = view.findViewById(R.id.tvPrayerStatus)
            val btnDone: Button = view.findViewById(R.id.btnDone)
            val btnMissed: Button = view.findViewById(R.id.btnMissed)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_prayer_child, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val prayerName = prayers[position]
            val log = logsMap[prayerName]
            val status = log?.status ?: "PENDING"

            holder.tvPrayerName.text = prayerName
            holder.tvPrayerTime.text = calculatePrayerTime(prayerName)
            holder.tvPrayerStatus.text = status

            holder.tvPrayerStatus.setTextColor(
                when (status) {
                    "DONE" -> 0xFF4CAF50.toInt()
                    "MISSED" -> 0xFFF44336.toInt()
                    else -> 0xFFFF9800.toInt()
                }
            )

            if (status == "PENDING") {
                holder.btnDone.visibility = View.VISIBLE
                holder.btnMissed.visibility = View.VISIBLE
            } else {
                holder.btnDone.visibility = View.GONE
                holder.btnMissed.visibility = View.GONE
            }

            holder.btnDone.setOnClickListener {
                showParentPasswordDialog { markPrayer(prayerName, "DONE") }
            }
            holder.btnMissed.setOnClickListener { showMissedDialog(prayerName) }
        }

        override fun getItemCount() = prayers.size

        fun updateData(newLogs: Map<String, PrayerLog>) {
            logsMap = newLogs
            notifyDataSetChanged()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}