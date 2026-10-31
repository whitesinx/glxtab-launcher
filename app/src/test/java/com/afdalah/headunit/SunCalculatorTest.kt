package com.afdalah.headunit

import com.afdalah.headunit.sensors.SunCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime
import java.time.ZoneOffset
import kotlin.math.abs

class SunCalculatorTest {
    private fun utc(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        ZonedDateTime.of(y, mo, d, h, mi, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun assertNear(expected: Long, actual: Long?, minutes: Int = 5) {
        val diff = abs(expected - actual!!) / 60_000
        assertTrue("off by $diff min", diff <= minutes)
    }

    @Test fun londonSummerSolstice() {
        // London 2024-06-21: sunrise 03:43 UTC, sunset 20:21 UTC.
        val t = SunCalculator.times(utc(2024, 6, 21, 12, 0), 51.5074, -0.1278)
        assertNear(utc(2024, 6, 21, 3, 43), t.sunrise)
        assertNear(utc(2024, 6, 21, 20, 21), t.sunset)
    }

    @Test fun jakartaNearEquator() {
        // Jakarta 2024-03-20: sunrise ~22:59 UTC (prev day), sunset ~11:06 UTC.
        val t = SunCalculator.times(utc(2024, 3, 20, 5, 0), -6.2088, 106.8456)
        assertNear(utc(2024, 3, 19, 22, 59), t.sunrise)
        assertNear(utc(2024, 3, 20, 11, 6), t.sunset)
    }

    @Test fun isNightAcrossTimeZones() {
        assertFalse(SunCalculator.isNight(utc(2024, 6, 21, 12, 0), 51.5074, -0.1278))
        assertTrue(SunCalculator.isNight(utc(2024, 6, 21, 23, 30), 51.5074, -0.1278))
        // 14:00 UTC = 21:00 in Jakarta -> night; 03:00 UTC = 10:00 -> day
        assertTrue(SunCalculator.isNight(utc(2024, 3, 20, 14, 0), -6.2088, 106.8456))
        assertFalse(SunCalculator.isNight(utc(2024, 3, 20, 3, 0), -6.2088, 106.8456))
    }

    @Test fun polarNightAndDay() {
        assertTrue(SunCalculator.times(utc(2024, 12, 21, 12, 0), 78.22, 15.65).polarNight) // Svalbard
        assertTrue(SunCalculator.times(utc(2024, 6, 21, 12, 0), 78.22, 15.65).polarDay)
        assertEquals(false, SunCalculator.isNight(utc(2024, 6, 21, 0, 0), 78.22, 15.65))
    }
}
