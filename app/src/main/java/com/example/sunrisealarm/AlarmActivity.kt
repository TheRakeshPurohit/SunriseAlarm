package com.example.sunrisealarm

import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.sunrisealarm.receiver.AlarmReceiver
import com.example.sunrisealarm.util.AlarmScheduler
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class AlarmActivity : AppCompatActivity() {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var effectsStopped = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        turnScreenOnAndShowWhenLocked()
        setContentView(R.layout.activity_alarm)

        findViewById<TextView>(R.id.tvAlarmTime).text =
            LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a"))

        findViewById<Button>(R.id.btnDismiss).setOnClickListener { dismissAlarm() }
        findViewById<Button>(R.id.btnSnooze).setOnClickListener { snoozeAlarm() }

        startAlarmEffects()
    }

    override fun onDestroy() {
        stopAlarmEffects()
        super.onDestroy()
    }

    private fun dismissAlarm() {
        stopAlarmEffects()
        AlarmScheduler.scheduleNextSunrise(this)
        finish()
    }

    private fun snoozeAlarm() {
        stopAlarmEffects()
        AlarmScheduler.scheduleAt(this, System.currentTimeMillis() + 5 * 60 * 1000L)
        finish()
    }

    private fun startAlarmEffects() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ringtone = RingtoneManager.getRingtone(applicationContext, uri)?.also { rt ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    rt.isLooping = true
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    rt.audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                }
                rt.play()
            }
        } catch (_: Exception) {
        }

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(VibratorManager::class.java)
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as? Vibrator
        }

        try {
            val pattern = longArrayOf(0, 500, 500, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (_: Exception) {
        }
    }

    private fun stopAlarmEffects() {
        if (effectsStopped) return
        effectsStopped = true
        try {
            ringtone?.stop()
        } catch (_: Exception) {
        }
        ringtone = null
        try {
            vibrator?.cancel()
        } catch (_: Exception) {
        }
        vibrator = null
        AlarmReceiver.cancelNotification(this)
    }

    private fun turnScreenOnAndShowWhenLocked() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
