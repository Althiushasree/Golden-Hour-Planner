package com.example

import com.example.model.SolarEventType
import com.example.model.SolarResults
import com.example.utils.SolarTimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class SolarTimeUtilsTest {

    @Test
    fun `toSolarTimings calculates morning and evening golden hours accurately`() {
        val date = LocalDate.of(2026, 11, 16)
        val zone = ZoneId.of("America/Los_Angeles")
        val results = SolarResults(
            sunrise = "2026-11-16T14:50:00+00:00",
            sunset = "2026-11-17T00:58:00+00:00",
            solarNoon = "2026-11-16T19:54:00+00:00",
            dayLength = "36480",
            civilTwilightBegin = "2026-11-16T14:21:00+00:00",
            civilTwilightEnd = "2026-11-17T01:27:00+00:00",
            nauticalTwilightBegin = null,
            nauticalTwilightEnd = null,
            astronomicalTwilightBegin = null,
            astronomicalTwilightEnd = null
        )

        val timings = SolarTimeUtils.toSolarTimings(date, results, zoneId = zone)

        assertEquals(Instant.parse("2026-11-16T14:50:00Z"), timings.sunrise)
        assertEquals(Instant.parse("2026-11-16T15:50:00Z"), timings.morningGoldenHourEnd)
        assertEquals(Instant.parse("2026-11-16T23:58:00Z"), timings.eveningGoldenHourStart)
        assertEquals(Instant.parse("2026-11-17T00:58:00Z"), timings.sunset)
        assertEquals("10h 8m", SolarTimeUtils.formatDayLength(timings.dayLengthSeconds))
        assertNotNull(SolarTimeUtils.formatLocalTimeRange(timings.morningGoldenHourStart, timings.morningGoldenHourEnd, zone))
    }

    @Test
    fun `parseUtcInstant and parseDayLengthSeconds handle offset Z and HHMMSS formats`() {
        assertEquals(
            Instant.parse("2026-11-16T14:50:00Z"),
            SolarTimeUtils.parseUtcInstant("2026-11-16T14:50:00+00:00")
        )
        assertEquals(
            Instant.parse("2026-11-16T14:50:00Z"),
            SolarTimeUtils.parseUtcInstant("2026-11-16T14:50:00Z")
        )
        assertEquals(36480L, SolarTimeUtils.parseDayLengthSeconds("36480"))
        assertEquals(36480L, SolarTimeUtils.parseDayLengthSeconds("10:08:00"))
        assertNull(SolarTimeUtils.parseDayLengthSeconds("0"))
    }

    @Test
    fun `polar sentinel timestamps parse as null`() {
        assertNull(SolarTimeUtils.parseUtcInstant("1970-01-01T00:00:00+00:00"))
        assertNull(SolarTimeUtils.parseUtcInstant(""))
        assertNull(SolarTimeUtils.parseUtcInstant(null))
    }

    @Test
    fun `countdown rolls to tomorrow sunrise after today sunset passes`() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.now(zone)
        val tomorrow = today.plusDays(1)

        val todaySunrise = today.atTime(6, 0).atZone(zone).toInstant()
        val todaySunset = today.atTime(18, 0).atZone(zone).toInstant()
        val tomorrowSunrise = tomorrow.atTime(6, 2).atZone(zone).toInstant()
        val tomorrowSunset = tomorrow.atTime(18, 0).atZone(zone).toInstant()

        val todayTimings = SolarTimeUtils.toSolarTimings(
            today,
            SolarResults(
                sunrise = todaySunrise.toString(),
                sunset = todaySunset.toString(),
                solarNoon = null,
                dayLength = "43200",
                civilTwilightBegin = null,
                civilTwilightEnd = null,
                nauticalTwilightBegin = null,
                nauticalTwilightEnd = null,
                astronomicalTwilightBegin = null,
                astronomicalTwilightEnd = null
            ),
            zoneId = zone
        )
        val tomorrowTimings = SolarTimeUtils.toSolarTimings(
            tomorrow,
            SolarResults(
                sunrise = tomorrowSunrise.toString(),
                sunset = tomorrowSunset.toString(),
                solarNoon = null,
                dayLength = "43080",
                civilTwilightBegin = null,
                civilTwilightEnd = null,
                nauticalTwilightBegin = null,
                nauticalTwilightEnd = null,
                astronomicalTwilightBegin = null,
                astronomicalTwilightEnd = null
            ),
            zoneId = zone
        )

        val afterSunsetNow = today.atTime(20, 0).atZone(zone).toInstant()
        val countdown = SolarTimeUtils.computeCountdown(
            now = afterSunsetNow,
            selectedDate = today,
            selectedTimings = todayTimings,
            todayTimings = todayTimings,
            tomorrowTimings = tomorrowTimings,
            zoneId = zone
        )

        assertEquals(SolarEventType.NEXT_SUNRISE, countdown.eventType)
        assertNotNull(countdown.targetInstant)
        assertEquals(10L, countdown.hours)
        assertEquals(2L, countdown.minutes)
        assertEquals(0L, countdown.seconds)
        assertFalse(countdown.isSelectedDatePast)
    }

    @Test
    fun `past selected date produces non-negative countdown with past flag`() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.now(zone)
        val yesterday = today.minusDays(1)
        val tomorrow = today.plusDays(1)

        val todaySunrise = today.atTime(6, 0).atZone(zone).toInstant()
        val todaySunset = today.atTime(18, 0).atZone(zone).toInstant()
        val tomorrowSunrise = tomorrow.atTime(6, 1).atZone(zone).toInstant()

        val pastTimings = SolarTimeUtils.toSolarTimings(
            yesterday,
            SolarResults(
                sunrise = yesterday.atTime(6, 0).atZone(zone).toInstant().toString(),
                sunset = yesterday.atTime(18, 0).atZone(zone).toInstant().toString(),
                solarNoon = null,
                dayLength = "43200",
                civilTwilightBegin = null,
                civilTwilightEnd = null,
                nauticalTwilightBegin = null,
                nauticalTwilightEnd = null,
                astronomicalTwilightBegin = null,
                astronomicalTwilightEnd = null
            ),
            zoneId = zone
        )
        val todayTimings = SolarTimeUtils.toSolarTimings(
            today,
            SolarResults(
                sunrise = todaySunrise.toString(),
                sunset = todaySunset.toString(),
                solarNoon = null,
                dayLength = "43200",
                civilTwilightBegin = null,
                civilTwilightEnd = null,
                nauticalTwilightBegin = null,
                nauticalTwilightEnd = null,
                astronomicalTwilightBegin = null,
                astronomicalTwilightEnd = null
            ),
            zoneId = zone
        )
        val tomorrowTimings = SolarTimeUtils.toSolarTimings(
            tomorrow,
            SolarResults(
                sunrise = tomorrowSunrise.toString(),
                sunset = tomorrow.atTime(18, 0).atZone(zone).toInstant().toString(),
                solarNoon = null,
                dayLength = "43200",
                civilTwilightBegin = null,
                civilTwilightEnd = null,
                nauticalTwilightBegin = null,
                nauticalTwilightEnd = null,
                astronomicalTwilightBegin = null,
                astronomicalTwilightEnd = null
            ),
            zoneId = zone
        )

        val noonToday = today.atTime(12, 0).atZone(zone).toInstant()
        val countdown = SolarTimeUtils.computeCountdown(
            now = noonToday,
            selectedDate = yesterday,
            selectedTimings = pastTimings,
            todayTimings = todayTimings,
            tomorrowTimings = tomorrowTimings,
            zoneId = zone
        )

        assertTrue(countdown.isSelectedDatePast)
        assertEquals(SolarEventType.NEXT_SUNSET, countdown.eventType)
        assertEquals(6L, countdown.hours)
    }
}
