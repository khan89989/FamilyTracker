package com.family.tracker.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import java.util.Date

class PrayerReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("prayer_prefs", Context.MODE_PRIVATE)

        // Ensure prayer reminders are actually enabled by the parent
        val isEnabled = prefs.getBoolean("reminder_prayers", false)
        if (!isEnabled) return Result.success()

        // Get Location
        var latitude = prefs.getFloat("lat", -1f).toDouble()
        var longitude = prefs.getFloat("lon", -1f).toDouble()
        if (latitude == -1.0 || longitude == -1.0) {
            latitude = 21.4225 // Makkah fallback
            longitude = 39.8262
        }

        // Get Method
        val methodId = prefs.getString("method", "MUSLIM_WORLD_LEAGUE")
        val method = when (methodId) {
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

        // Calculate Times
        val coordinates = Coordinates(latitude, longitude)
        val dateComponents = DateComponents.from(Date())
        val params = method.parameters
        params.madhab = Madhab.SHAFI
        val times = PrayerTimes(coordinates, dateComponents, params)

        // Schedule Alarms
        scheduleAlarm("Fajr", times.fajr.time)
        scheduleAlarm("Dhuhr", times.dhuhr.time)
        scheduleAlarm("Asr", times.asr.time)
        scheduleAlarm("Maghrib", times.maghrib.time)
        scheduleAlarm("Isha", times.isha.time)

        return Result.success()
    }

    private fun scheduleAlarm(prayerName: String, triggerTimeMillis: Long) {
        val alarmManager = applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(applicationContext, PrayerAlarmReceiver::class.java).apply {
            putExtra("PRAYER_NAME", prayerName)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            applicationContext, prayerName.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pendingIntent)
        }
    }
}