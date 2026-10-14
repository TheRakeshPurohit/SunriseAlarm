package com.example.sunrisealarm.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.sunrisealarm.AlarmActivity
import com.example.sunrisealarm.R
import com.example.sunrisealarm.util.AlarmScheduler

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_FIRE) return
        val app = context.applicationContext
        startRing(app)
    }

    companion object {
        const val ACTION_FIRE = "com.example.sunrisealarm.ACTION_FIRE"
        const val CHANNEL_ID = "sunrise_alarm_channel"
        const val NOTIFICATION_ID = 42

        fun startRing(context: Context) {
            val app = context.applicationContext
            ensureChannel(app)

            val fullScreen = Intent(app, AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val fullScreenPi = PendingIntent.getActivity(
                app,
                AlarmScheduler.REQUEST_SHOW,
                fullScreen,
                AlarmScheduler.pendingFlags()
            )

            val notification = NotificationCompat.Builder(app, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(app.getString(R.string.alarm_notification_title))
                .setContentText(app.getString(R.string.alarm_notification_text))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(fullScreenPi)
                .setFullScreenIntent(fullScreenPi, true)
                .build()

            val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, notification)

            // Older APIs / when FSI not restricted: also try starting the activity.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                try {
                    app.startActivity(fullScreen)
                } catch (_: Exception) {
                }
            }
        }

        fun cancelNotification(context: Context) {
            val nm = context.applicationContext
                .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIFICATION_ID)
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.alarm_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.alarm_channel_desc)
                setBypassDnd(true)
                enableVibration(true)
            }
            nm.createNotificationChannel(channel)
        }
    }
}
