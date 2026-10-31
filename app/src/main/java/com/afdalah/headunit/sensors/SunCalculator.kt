package com.afdalah.headunit.sensors

import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Sunrise/sunset from the standard sunrise equation (≈1 minute accuracy, enough for theming).
 * No network, no Play Services.
 */
object SunCalculator {

    data class SunTimes(val sunrise: Long?, val sunset: Long?, val polarDay: Boolean = false, val polarNight: Boolean = false)

    private const val J2000 = 2451545.0
    private const val UNIX_EPOCH_JD = 2440587.5
    private const val DAY_MS = 86_400_000.0

    private fun toJulian(millis: Long) = millis / DAY_MS + UNIX_EPOCH_JD
    private fun fromJulian(jd: Double) = ((jd - UNIX_EPOCH_JD) * DAY_MS).toLong()
    private fun rad(d: Double) = Math.toRadians(d)
    private fun deg(r: Double) = Math.toDegrees(r)

    /** Sun times for the solar day `dayOffset` days from the one containing [millis]. */
    fun times(millis: Long, lat: Double, lon: Double, dayOffset: Int = 0): SunTimes {
        val n = floor(toJulian(millis) - J2000 + 0.0008 + lon / 360.0 + 0.5) + dayOffset
        val jStar = n - lon / 360.0
        val m = (357.5291 + 0.98560028 * jStar).mod(360.0)
        val c = 1.9148 * sin(rad(m)) + 0.02 * sin(rad(2 * m)) + 0.0003 * sin(rad(3 * m))
        val lambda = (m + c + 180.0 + 102.9372).mod(360.0)
        val transit = J2000 + jStar + 0.0053 * sin(rad(m)) - 0.0069 * sin(rad(2 * lambda))
        val decl = asin(sin(rad(lambda)) * sin(rad(23.4397)))
        val cosW = (sin(rad(-0.833)) - sin(rad(lat)) * sin(decl)) / (cos(rad(lat)) * cos(decl))
        if (cosW < -1) return SunTimes(null, null, polarDay = true)
        if (cosW > 1) return SunTimes(null, null, polarNight = true)
        val w = deg(acos(cosW))
        return SunTimes(fromJulian(transit - w / 360.0), fromJulian(transit + w / 360.0))
    }

    fun isNight(now: Long, lat: Double, lon: Double): Boolean {
        for (offset in -1..1) {
            val t = times(now, lat, lon, offset)
            if (t.polarDay) return false
            if (t.polarNight) return true
            if (t.sunrise != null && t.sunset != null && now in t.sunrise..t.sunset) return false
        }
        return true
    }

    /** Next sunrise or sunset after [now], for the "Sunset 18:42" hint. */
    fun nextTransition(now: Long, lat: Double, lon: Double): Pair<Boolean, Long>? =
        (-1..2).flatMap { o ->
            val t = times(now, lat, lon, o)
            listOfNotNull(t.sunrise?.let { true to it }, t.sunset?.let { false to it })
        }.filter { it.second > now }.minByOrNull { it.second }

    /** Fallback without any location: night between 19:00 and 07:00 local time. */
    fun isNightByClock(hourOfDay: Int) = hourOfDay >= 19 || hourOfDay < 7
}
