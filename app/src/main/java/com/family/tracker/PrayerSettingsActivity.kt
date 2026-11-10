package com.family.tracker

import android.location.Address
import android.location.Geocoder
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.family.tracker.data.AppDatabase
import com.family.tracker.data.AppSettings
import com.family.tracker.databinding.ActivityPrayerSettingsBinding
import com.family.tracker.utils.AutoBackupHelper
import com.family.tracker.utils.HomeworkReminderWorker
import com.family.tracker.utils.PrayerAlarmScheduler
import com.family.tracker.utils.PrayerReminderWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class PrayerSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPrayerSettingsBinding
    private lateinit var db: AppDatabase

    private var selectedLat = 21.4225
    private var selectedLon = 39.8262
    private var selectedCityName = "Makkah"

    private var searchJob: Job? = null
    private var isSelectingSuggestion = false
    private var searchResults: List<Address> = emptyList()

    private val methods = listOf(
        "Muslim World League" to "MUSLIM_WORLD_LEAGUE",
        "Egyptian General Authority" to "EGYPTIAN",
        "University of Islamic Sciences, Karachi" to "KARACHI",
        "Umm al-Qura University, Makkah" to "UMM_AL_QURA",
        "Islamic Society of North America (ISNA)" to "NORTH_AMERICA",
        "Dubai" to "DUBAI",
        "Qatar" to "QATAR",
        "Kuwait" to "KUWAIT",
        "Singapore" to "SINGAPORE"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPrayerSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getDatabase(this)

        setupSpinner()
        setupAutocomplete()
        loadSavedSettings()

        binding.btnSearchCity.setOnClickListener {
            val city = binding.etSearchCity.text.toString().trim()
            if (city.isNotEmpty()) searchCity(city)
        }

        binding.btnUseGps.setOnClickListener {
            selectedLat = 21.4225 // Reset to Makkah fallback, it will use live GPS
            selectedLon = 39.8262
            selectedCityName = "Device GPS"
            binding.tvCurrentLocation.text = "Current: Using GPS / Default"
            binding.etSearchCity.setText("Device GPS", false)
            Toast.makeText(this, "Will use device GPS location", Toast.LENGTH_SHORT).show()
        }

        binding.btnSaveSettings.setOnClickListener {
            saveSettings()
        }
    }

    private fun setupAutocomplete() {
        binding.etSearchCity.setOnItemClickListener { parent, _, position, _ ->
            val address = searchResults.getOrNull(position) ?: return@setOnItemClickListener
            isSelectingSuggestion = true

            selectedLat = address.latitude
            selectedLon = address.longitude
            selectedCityName = "${address.locality ?: address.subAdminArea ?: address.featureName}, ${address.countryName ?: ""}"

            binding.tvCurrentLocation.text = "Current: $selectedCityName"
            binding.etSearchCity.setText(selectedCityName, false)

            isSelectingSuggestion = false
            Toast.makeText(this, "Location Selected!", Toast.LENGTH_SHORT).show()
        }

        binding.etSearchCity.doAfterTextChanged { text ->
            if (isSelectingSuggestion) return@doAfterTextChanged
            val query = text.toString().trim()
            if (query.length < 3) return@doAfterTextChanged

            searchJob?.cancel()
            searchJob = lifecycleScope.launch(Dispatchers.IO) {
                delay(600)
                try {
                    val geocoder = Geocoder(this@PrayerSettingsActivity, Locale.getDefault())
                    val addresses = geocoder.getFromLocationName(query, 5)

                    withContext(Dispatchers.Main) {
                        if (!addresses.isNullOrEmpty()) {
                            searchResults = addresses
                            val displayNames = addresses.map { address ->
                                val city = address.locality ?: address.subAdminArea ?: address.featureName ?: "Unknown"
                                val country = address.countryName ?: ""
                                "$city, $country"
                            }
                            val adapter = ArrayAdapter(
                                this@PrayerSettingsActivity,
                                android.R.layout.simple_dropdown_item_1line,
                                displayNames
                            )
                            binding.etSearchCity.setAdapter(adapter)
                            if (binding.etSearchCity.isFocused) {
                                binding.etSearchCity.showDropDown()
                            }
                        }
                    }
                } catch (e: Exception) { }
            }
        }
    }

    private fun setupSpinner() {
        val methodNames = methods.map { it.first }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, methodNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerMethod.adapter = adapter
    }

    private fun loadSavedSettings() {
        lifecycleScope.launch(Dispatchers.IO) {
            val settings = db.settingsDao().getSettings()
            withContext(Dispatchers.Main) {
                if (settings != null) {
                    binding.switchHomeworkReminders.isChecked = settings.homeworkReminder
                    binding.switchPrayerReminders.isChecked = settings.prayerReminder

                    selectedLat = settings.latitude
                    selectedLon = settings.longitude
                    selectedCityName = settings.cityName

                    binding.tvCurrentLocation.text = "Current: $selectedCityName"
                    binding.etSearchCity.setText(selectedCityName, false)

                    val index = methods.indexOfFirst { it.second == settings.methodId }
                    if (index != -1) binding.spinnerMethod.setSelection(index)

                    val enabledAdhans = settings.enabledAdhans.split(",")
                    binding.cbFajr.isChecked = enabledAdhans.contains("Fajr")
                    binding.cbDhuhr.isChecked = enabledAdhans.contains("Dhuhr")
                    binding.cbAsr.isChecked = enabledAdhans.contains("Asr")
                    binding.cbMaghrib.isChecked = enabledAdhans.contains("Maghrib")
                    binding.cbIsha.isChecked = enabledAdhans.contains("Isha")
                }
            }
        }
    }

    private fun searchCity(city: String) {
        Toast.makeText(this, "Searching...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(this@PrayerSettingsActivity, Locale.getDefault())
                val addresses = geocoder.getFromLocationName(city, 1)

                withContext(Dispatchers.Main) {
                    if (!addresses.isNullOrEmpty()) {
                        val address = addresses[0]
                        selectedLat = address.latitude
                        selectedLon = address.longitude
                        selectedCityName = "${address.locality ?: city}, ${address.countryName ?: ""}"

                        binding.tvCurrentLocation.text = "Current: $selectedCityName"
                        Toast.makeText(this@PrayerSettingsActivity, "Location Found!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@PrayerSettingsActivity, "City not found.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PrayerSettingsActivity, "Search failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun saveSettings() {
        val selectedMethodName = methods[binding.spinnerMethod.selectedItemPosition].second
        val hwEnabled = binding.switchHomeworkReminders.isChecked
        val prayersEnabled = binding.switchPrayerReminders.isChecked

        val enabledList = mutableListOf<String>()
        if (binding.cbFajr.isChecked) enabledList.add("Fajr")
        if (binding.cbDhuhr.isChecked) enabledList.add("Dhuhr")
        if (binding.cbAsr.isChecked) enabledList.add("Asr")
        if (binding.cbMaghrib.isChecked) enabledList.add("Maghrib")
        if (binding.cbIsha.isChecked) enabledList.add("Isha")
        val enabledAdhansString = enabledList.joinToString(",")

        lifecycleScope.launch(Dispatchers.IO) {
            val existingSettings = db.settingsDao().getSettings() ?: AppSettings()

            val settings = existingSettings.copy(
                latitude = selectedLat,
                longitude = selectedLon,
                cityName = selectedCityName,
                methodId = selectedMethodName,
                homeworkReminder = hwEnabled,
                prayerReminder = prayersEnabled,
                enabledAdhans = enabledAdhansString
            )
            db.settingsDao().insertOrUpdate(settings)

            if (hwEnabled) scheduleHomeworkReminders() else cancelHomeworkReminders()
            if (prayersEnabled) schedulePrayerReminders() else cancelPrayerReminders()

            // Schedule actual alarms for today
            PrayerAlarmScheduler.scheduleAlarms(this@PrayerSettingsActivity, settings)

            AutoBackupHelper.triggerSilentBackup(this@PrayerSettingsActivity)

            withContext(Dispatchers.Main) {
                Toast.makeText(this@PrayerSettingsActivity, "Settings Saved & Synced!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun scheduleHomeworkReminders() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build()
        val request = PeriodicWorkRequestBuilder<HomeworkReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(calculateDelayTo4PM(), TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "HomeworkReminder", ExistingPeriodicWorkPolicy.UPDATE, request
        )
    }

    private fun cancelHomeworkReminders() {
        WorkManager.getInstance(this).cancelUniqueWork("HomeworkReminder")
    }

    private fun schedulePrayerReminders() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build()
        val request = PeriodicWorkRequestBuilder<PrayerReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(calculateDelayToMidnight(), TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "PrayerReminderScheduler", ExistingPeriodicWorkPolicy.UPDATE, request
        )
        WorkManager.getInstance(this).enqueue(
            androidx.work.OneTimeWorkRequestBuilder<PrayerReminderWorker>().build()
        )
    }

    private fun cancelPrayerReminders() {
        WorkManager.getInstance(this).cancelUniqueWork("PrayerReminderScheduler")
    }

    private fun calculateDelayTo4PM(): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 16); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
        if (target.before(now)) target.add(Calendar.DAY_OF_YEAR, 1)
        return target.timeInMillis - now.timeInMillis
    }

    private fun calculateDelayToMidnight(): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 1); set(Calendar.SECOND, 0) }
        return target.timeInMillis - now.timeInMillis
    }
}