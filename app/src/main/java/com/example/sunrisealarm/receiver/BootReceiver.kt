package com.example.sunrisealarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.sunrisealarm.util.AlarmScheduler
import com.example.sunrisealarm.util.SunriseAlarmPrefs

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val app = context.applicationContext
        if (!SunriseAlarmPrefs.isAutoAlarmEnabled(app)) {
            Log.d(TAG, "Auto alarm off; skip reschedule")
            return
        }
        val ok = AlarmScheduler.scheduleNextSunrise(app)
        Log.d(TAG, "Rescheduled from cache: $ok")
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
