package com.example.sunrisealarm.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

object LocationHelper {
    private const val STALE_MS = 7L * 24 * 60 * 60 * 1000
    private const val TIMEOUT_MS = 12_000L

    fun interface ResultCallback {
        fun onResult(lat: Double, lon: Double, fromCacheOrDefault: Boolean)
    }

    fun resolveLocation(context: Context, callback: ResultCallback) {
        val app = context.applicationContext
        val cachedFresh = isCacheFresh(app)
        if (cachedFresh) {
            callback.onResult(
                SunriseAlarmPrefs.getLatitude(app),
                SunriseAlarmPrefs.getLongitude(app),
                true
            )
            return
        }

        if (!hasLocationPermission(app)) {
            deliverFallback(app, callback)
            return
        }

        val lm = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val last = bestLastKnown(lm)
        if (last != null && !isStale(last.time)) {
            SunriseAlarmPrefs.saveLocation(app, last.latitude, last.longitude)
            callback.onResult(last.latitude, last.longitude, false)
            return
        }

        requestOneUpdate(app, lm, last, callback)
    }

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun isCacheFresh(context: Context): Boolean {
        if (!SunriseAlarmPrefs.hasCachedLocation(context)) return false
        val updated = SunriseAlarmPrefs.getUpdatedAt(context)
        return updated > 0L && !isStale(updated)
    }

    private fun isStale(epochMs: Long): Boolean =
        System.currentTimeMillis() - epochMs > STALE_MS

    private fun deliverFallback(context: Context, callback: ResultCallback) {
        if (SunriseAlarmPrefs.hasCachedLocation(context)) {
            callback.onResult(
                SunriseAlarmPrefs.getLatitude(context),
                SunriseAlarmPrefs.getLongitude(context),
                true
            )
        } else {
            callback.onResult(
                SunriseCalculator.SURAT_LATITUDE,
                SunriseCalculator.SURAT_LONGITUDE,
                true
            )
        }
    }

    private fun bestLastKnown(lm: LocationManager): Location? {
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        var best: Location? = null
        for (provider in providers) {
            if (!lm.isProviderEnabled(provider)) continue
            val loc = try {
                lm.getLastKnownLocation(provider)
            } catch (_: SecurityException) {
                null
            } ?: continue
            if (best == null || loc.time > best.time) best = loc
        }
        return best
    }

    private fun requestOneUpdate(
        app: Context,
        lm: LocationManager,
        fallbackLast: Location?,
        callback: ResultCallback
    ) {
        val handler = Handler(Looper.getMainLooper())
        var finished = false

        fun finish(lat: Double, lon: Double, fromCacheOrDefault: Boolean) {
            if (finished) return
            finished = true
            callback.onResult(lat, lon, fromCacheOrDefault)
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                handler.removeCallbacksAndMessages(null)
                try {
                    lm.removeUpdates(this)
                } catch (_: Exception) {
                }
                SunriseAlarmPrefs.saveLocation(app, location.latitude, location.longitude)
                finish(location.latitude, location.longitude, false)
            }

            @Deprecated("Deprecated in API")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        val timeout = Runnable {
            try {
                lm.removeUpdates(listener)
            } catch (_: Exception) {
            }
            if (fallbackLast != null) {
                SunriseAlarmPrefs.saveLocation(app, fallbackLast.latitude, fallbackLast.longitude)
                finish(fallbackLast.latitude, fallbackLast.longitude, false)
            } else if (SunriseAlarmPrefs.hasCachedLocation(app)) {
                finish(
                    SunriseAlarmPrefs.getLatitude(app),
                    SunriseAlarmPrefs.getLongitude(app),
                    true
                )
            } else {
                finish(
                    SunriseCalculator.SURAT_LATITUDE,
                    SunriseCalculator.SURAT_LONGITUDE,
                    true
                )
            }
        }

        val provider = when {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }

        if (provider == null) {
            if (fallbackLast != null) {
                SunriseAlarmPrefs.saveLocation(app, fallbackLast.latitude, fallbackLast.longitude)
                finish(fallbackLast.latitude, fallbackLast.longitude, false)
            } else {
                deliverFallback(app, callback)
            }
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                lm.getCurrentLocation(provider, null, app.mainExecutor) { location ->
                    handler.removeCallbacks(timeout)
                    if (location != null) {
                        SunriseAlarmPrefs.saveLocation(app, location.latitude, location.longitude)
                        finish(location.latitude, location.longitude, false)
                    } else {
                        timeout.run()
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            }
            handler.postDelayed(timeout, TIMEOUT_MS)
        } catch (_: SecurityException) {
            deliverFallback(app, callback)
        } catch (_: Exception) {
            deliverFallback(app, callback)
        }
    }
}
