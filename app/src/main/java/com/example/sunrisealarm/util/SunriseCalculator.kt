package com.example.sunrisealarm.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.*

/**
 * Self-contained astronomical calculator for sunrise and sunset.
 * Based on the NOAA Solar Calculator algorithm.
 * No internet connection or external libraries required.
 */
object SunriseCalculator {

    // Default coordinates: Surat, Gujarat, India
    const val SURAT_LATITUDE = 21.1702
    const val SURAT_LONGITUDE = 72.8311

    /**
     * Calculates the sunrise time for a given date and geographic coordinates.
     * @param date The target date
     * @param latitude North latitude in degrees
     * @param longitude East longitude in degrees
     * @param zoneId Time zone of the target location
     * @return LocalDateTime of sunrise, or null if polar day/night
     */
    fun calculateSunrise(
        date: LocalDate,
        latitude: Double = SURAT_LATITUDE,
        longitude: Double = SURAT_LONGITUDE,
        zoneId: ZoneId = ZoneId.of("Asia/Kolkata")
    ): LocalDateTime? {
        val dayOfYear = date.dayOfYear

        // Convert longitude to hour value and estimate time
        val lngHour = longitude / 15.0
        val t = dayOfYear + ((6.0 - lngHour) / 24.0)

        // Sun's mean anomaly
        val m = (0.9856 * t) - 3.289

        // Sun's true longitude
        var l = m + (1.916 * sin(Math.toRadians(m))) + (0.020 * sin(Math.toRadians(2 * m))) + 282.634
        l = normalizeDegrees(l)

        // Sun's right ascension
        var ra = Math.toDegrees(atan(0.91764 * tan(Math.toRadians(l))))
        ra = normalizeDegrees(ra)

        // Right ascension value needs to be in the same quadrant as L
        val lQuadrant = floor(l / 90.0) * 90.0
        val raQuadrant = floor(ra / 90.0) * 90.0
        ra += (lQuadrant - raQuadrant)
        ra /= 15.0 // Convert to hours

        // Sun's declination
        val sinDec = 0.39782 * sin(Math.toRadians(l))
        val cosDec = cos(asin(sinDec))

        // Sun's local hour angle for standard sunrise zenith (90° 50' = 90.8333°)
        val zenith = 90.8333
        val cosH = (cos(Math.toRadians(zenith)) - (sinDec * sin(Math.toRadians(latitude)))) /
                (cosDec * cos(Math.toRadians(latitude)))

        // Sun never rises at this location on this date
        if (cosH > 1.0 || cosH < -1.0) {
            return null
        }

        // Local hour angle for sunrise in hours
        val h = (360.0 - Math.toDegrees(acos(cosH))) / 15.0

        // Local mean time of rising
        val localMeanTime = h + ra - (0.06571 * t) - 6.622

        // Universal Time (UTC) in hours
        var ut = localMeanTime - lngHour
        ut = (ut % 24.0 + 24.0) % 24.0

        // Convert UTC decimal hours to LocalTime in the target zone
        val utcSeconds = (ut * 3600.0).roundToLong()
        val utcTime = LocalTime.ofSecondOfDay(utcSeconds.coerceIn(0, 86399))
        val utcDateTime = LocalDateTime.of(date, utcTime)

        return utcDateTime.atZone(ZoneId.of("UTC"))
            .withZoneSameInstant(zoneId)
            .toLocalDateTime()
    }

    private fun normalizeDegrees(degrees: Double): Double {
        var res = degrees % 360.0
        if (res < 0.0) res += 360.0
        return res
    }
}
