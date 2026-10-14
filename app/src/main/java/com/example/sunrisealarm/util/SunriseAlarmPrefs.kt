package com.example.sunrisealarm.util

import android.content.Context
import android.content.SharedPreferences

object SunriseAlarmPrefs {
    const val PREFS_NAME = "SunriseAlarmPrefs"
    const val KEY_AUTO_ALARM = "auto_alarm_enabled"
    const val KEY_LAT = "cached_lat"
    const val KEY_LON = "cached_lon"
    const val KEY_UPDATED_AT = "location_updated_at"

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isAutoAlarmEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_ALARM, false)

    fun setAutoAlarmEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_ALARM, enabled).apply()
    }

    fun getLatitude(context: Context): Double =
        prefs(context).getFloat(KEY_LAT, SunriseCalculator.SURAT_LATITUDE.toFloat()).toDouble()

    fun getLongitude(context: Context): Double =
        prefs(context).getFloat(KEY_LON, SunriseCalculator.SURAT_LONGITUDE.toFloat()).toDouble()

    fun getUpdatedAt(context: Context): Long =
        prefs(context).getLong(KEY_UPDATED_AT, 0L)

    fun hasCachedLocation(context: Context): Boolean =
        prefs(context).contains(KEY_LAT) && prefs(context).contains(KEY_LON)

    fun saveLocation(context: Context, lat: Double, lon: Double) {
        prefs(context).edit()
            .putFloat(KEY_LAT, lat.toFloat())
            .putFloat(KEY_LON, lon.toFloat())
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }
}
