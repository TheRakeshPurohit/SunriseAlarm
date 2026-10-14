package com.example.sunrisealarm

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.example.sunrisealarm.util.AlarmScheduler
import com.example.sunrisealarm.util.LocationHelper
import com.example.sunrisealarm.util.SunriseAlarmPrefs
import com.example.sunrisealarm.util.SunriseCalculator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var tvLocation: TextView
    private lateinit var tvTomorrowSunrise: TextView
    private lateinit var tvTodaySunrise: TextView
    private lateinit var tvNextAlarm: TextView
    private lateinit var switchDailySync: SwitchCompat
    private lateinit var btnSetNow: Button

    private var suppressSwitchCallback = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.values.any { it }
        if (granted || LocationHelper.hasLocationPermission(this)) {
            locateAndSchedule()
        } else {
            Toast.makeText(this, R.string.location_denied_using_default, Toast.LENGTH_LONG).show()
            scheduleWithCurrentCoords()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvLocation = findViewById(R.id.tvLocation)
        tvTomorrowSunrise = findViewById(R.id.tvTomorrowSunrise)
        tvTodaySunrise = findViewById(R.id.tvTodaySunrise)
        tvNextAlarm = findViewById(R.id.tvNextAlarm)
        switchDailySync = findViewById(R.id.switchDailySync)
        btnSetNow = findViewById(R.id.btnSetNow)

        displayCalculations()
        setupListeners()
        promptExactAlarmIfNeeded()
        promptFullScreenIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        displayCalculations()
    }

    private fun displayCalculations() {
        val lat = SunriseAlarmPrefs.getLatitude(this)
        val lon = SunriseAlarmPrefs.getLongitude(this)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val tomorrow = today.plusDays(1)
        val formatter = DateTimeFormatter.ofPattern("hh:mm a")

        val todaySunrise = SunriseCalculator.calculateSunrise(today, lat, lon, zone)
        val tomorrowSunrise = SunriseCalculator.calculateSunrise(tomorrow, lat, lon, zone)

        val label = if (SunriseAlarmPrefs.hasCachedLocation(this)) {
            getString(R.string.location_cached_fmt, lat, lon)
        } else {
            getString(R.string.location_default_surat)
        }
        tvLocation.text = label
        tvTodaySunrise.text = todaySunrise?.format(formatter) ?: "N/A"
        tvTomorrowSunrise.text = tomorrowSunrise?.format(formatter) ?: "N/A"

        val next = nextSunriseMillis(lat, lon, zone)
        tvNextAlarm.text = if (next != null) {
            val local = Instant.ofEpochMilli(next).atZone(zone)
                .format(DateTimeFormatter.ofPattern("EEE hh:mm a", Locale.getDefault()))
            getString(R.string.next_alarm_fmt, local)
        } else {
            getString(R.string.next_alarm_none)
        }
    }

    private fun setupListeners() {
        suppressSwitchCallback = true
        switchDailySync.isChecked = SunriseAlarmPrefs.isAutoAlarmEnabled(this)
        suppressSwitchCallback = false

        switchDailySync.setOnCheckedChangeListener { _, isChecked ->
            if (suppressSwitchCallback) return@setOnCheckedChangeListener
            SunriseAlarmPrefs.setAutoAlarmEnabled(this, isChecked)
            if (isChecked) {
                ensurePermissionsThenLocate()
            } else {
                AlarmScheduler.cancel(this)
                Toast.makeText(this, R.string.alarm_disabled, Toast.LENGTH_SHORT).show()
                displayCalculations()
            }
        }

        btnSetNow.setOnClickListener {
            promptExactAlarmIfNeeded()
            if (!AlarmScheduler.canScheduleExact(this)) {
                Toast.makeText(this, R.string.exact_alarm_required, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            ensureNotificationPermission()
            val ok = AlarmScheduler.scheduleNextSunrise(this)
            Toast.makeText(
                this,
                if (ok) R.string.alarm_scheduled else R.string.alarm_schedule_failed,
                Toast.LENGTH_SHORT
            ).show()
            displayCalculations()
        }
    }

    private fun ensurePermissionsThenLocate() {
        promptExactAlarmIfNeeded()
        promptFullScreenIfNeeded()
        ensureNotificationPermission()

        val needed = mutableListOf<String>()
        if (!LocationHelper.hasLocationPermission(this)) {
            needed += Manifest.permission.ACCESS_FINE_LOCATION
            needed += Manifest.permission.ACCESS_COARSE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notif = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            )
            if (notif != PackageManager.PERMISSION_GRANTED) {
                needed += Manifest.permission.POST_NOTIFICATIONS
            }
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        } else {
            locateAndSchedule()
        }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
    }

    private fun locateAndSchedule() {
        LocationHelper.resolveLocation(this) { _, _, _ ->
            scheduleWithCurrentCoords()
        }
    }

    private fun scheduleWithCurrentCoords() {
        if (!AlarmScheduler.canScheduleExact(this)) {
            Toast.makeText(this, R.string.exact_alarm_required, Toast.LENGTH_LONG).show()
            displayCalculations()
            return
        }
        val ok = AlarmScheduler.scheduleNextSunrise(this)
        Toast.makeText(
            this,
            if (ok) R.string.alarm_enabled else R.string.alarm_schedule_failed,
            Toast.LENGTH_SHORT
        ).show()
        displayCalculations()
    }

    private fun promptExactAlarmIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (AlarmScheduler.canScheduleExact(this)) return
        try {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            } catch (_: Exception) {
            }
        }
    }

    private fun promptFullScreenIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.canUseFullScreenIntent()) return
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private fun nextSunriseMillis(lat: Double, lon: Double, zone: ZoneId): Long? {
        val now = System.currentTimeMillis()
        var date = LocalDate.now(zone)
        for (i in 0..2) {
            val sunrise = SunriseCalculator.calculateSunrise(date, lat, lon, zone) ?: continue
            val millis = sunrise.atZone(zone).toInstant().toEpochMilli()
            if (millis > now + 30_000L) return millis
            date = date.plusDays(1)
        }
        return null
    }
}
