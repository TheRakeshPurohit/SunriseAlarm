package com.example.sunrisealarm.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.sunrisealarm.AlarmActivity
import com.example.sunrisealarm.receiver.AlarmReceiver
import java.time.LocalDate
import java.time.ZoneId

object AlarmScheduler {
    private const val TAG = "AlarmScheduler"
    const val REQUEST_ALARM = 1001
    const val REQUEST_SHOW = 1002

    fun pendingFlags(): Int {
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        return flags
    }

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    fun cancel(context: Context) {
        val app = context.applicationContext
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(alarmPendingIntent(app))
        am.cancel(showPendingIntent(app))
    }

    fun scheduleAt(context: Context, epochMillis: Long): Boolean {
        val app = context.applicationContext
        if (!canScheduleExact(app)) {
            Log.w(TAG, "Exact alarms not permitted")
            return false
        }
        cancel(app)
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val show = showPendingIntent(app)
        val operation = alarmPendingIntent(app)
        val info = AlarmManager.AlarmClockInfo(epochMillis, show)
        am.setAlarmClock(info, operation)
        Log.d(TAG, "Scheduled alarmClock at $epochMillis")
        return true
    }

    fun scheduleNextSunrise(context: Context): Boolean {
        val app = context.applicationContext
        val lat = SunriseAlarmPrefs.getLatitude(app)
        val lon = SunriseAlarmPrefs.getLongitude(app)
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        var date = LocalDate.now(zone)
        for (i in 0..2) {
            val sunrise = SunriseCalculator.calculateSunrise(date, lat, lon, zone)
            if (sunrise != null) {
                val millis = sunrise.atZone(zone).toInstant().toEpochMilli()
                if (millis > now + 30_000L) {
                    return scheduleAt(app, millis)
                }
            }
            date = date.plusDays(1)
        }
        Log.e(TAG, "Could not compute a future sunrise")
        return false
    }

    private fun alarmPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE
        }
        return PendingIntent.getBroadcast(context, REQUEST_ALARM, intent, pendingFlags())
    }

    private fun showPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(context, REQUEST_SHOW, intent, pendingFlags())
    }
}
