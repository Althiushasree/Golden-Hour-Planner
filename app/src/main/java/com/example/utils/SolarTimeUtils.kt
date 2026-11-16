package com.example.utils

import com.example.model.CountdownInfo
import com.example.model.SolarEventType
import com.example.model.SolarResults
import com.example.model.SolarTimings
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

object SolarTimeUtils {

    private val apiDateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)

    private val displayDateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault())

    private val localTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

    private val updatedTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("h:mm:ss a", Locale.getDefault())

    fun formatApiDate(date: LocalDate): String = date.format(apiDateFormatter)

    fun formatDisplayDate(date: LocalDate): String = date.format(displayDateFormatter)

    fun parseUtcInstant(rawTimestamp: String?): Instant? {
        if (rawTimestamp.isNullOrBlank()) return null
        val trimmed = rawTimestamp.trim()
        val parsed = try {
            OffsetDateTime.parse(trimmed, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()
        } catch (_: Exception) {
            try {
                Instant.parse(trimmed)
            } catch (_: Exception) {
                null
            }
        } ?: return null

        // Sunrise-Sunset API returns 1970-01-01T00:00:00+00:00 when polar day/night makes an event unavailable.
        return if (parsed.epochSecond <= 86_400L) null else parsed
    }

    fun parseDayLengthSeconds(rawDayLength: String?): Long? {
        if (rawDayLength.isNullOrBlank()) return null
        val trimmed = rawDayLength.trim()
        trimmed.toLongOrNull()?.let { seconds ->
            return seconds.takeIf { it > 0L }
        }
        trimmed.toDoubleOrNull()?.toLong()?.let { seconds ->
            return seconds.takeIf { it > 0L }
        }
        val parts = trimmed.split(":")
        if (parts.size == 3) {
            val hours = parts[0].toLongOrNull() ?: return null
            val minutes = parts[1].toLongOrNull() ?: return null
            val seconds = parts[2].toLongOrNull() ?: return null
            val total = hours * 3600L + minutes * 60L + seconds
            return total.takeIf { it > 0L }
        }
        return null
    }

    fun toSolarTimings(
        selectedDate: LocalDate,
        results: SolarResults,
        zoneId: ZoneId = ZoneId.systemDefault(),
        lastUpdatedMillis: Long = System.currentTimeMillis()
    ): SolarTimings {
        val sunrise = parseUtcInstant(results.sunrise)
        val sunset = parseUtcInstant(results.sunset)
        val solarNoon = parseUtcInstant(results.solarNoon)
        val civilStart = parseUtcInstant(results.civilTwilightBegin)
        val civilEnd = parseUtcInstant(results.civilTwilightEnd)

        val morningGoldenStart = sunrise
        val morningGoldenEnd = sunrise?.plus(1, ChronoUnit.HOURS)

        val eveningGoldenStart = sunset?.minus(1, ChronoUnit.HOURS)
        val eveningGoldenEnd = sunset

        val validDayLength = parseDayLengthSeconds(results.dayLength)

        return SolarTimings(
            selectedDate = selectedDate,
            sunrise = sunrise,
            sunset = sunset,
            solarNoon = solarNoon,
            dayLengthSeconds = validDayLength,
            civilTwilightStart = civilStart,
            civilTwilightEnd = civilEnd,
            morningGoldenHourStart = morningGoldenStart,
            morningGoldenHourEnd = morningGoldenEnd,
            eveningGoldenHourStart = eveningGoldenStart,
            eveningGoldenHourEnd = eveningGoldenEnd,
            zoneId = zoneId,
            lastUpdatedMillis = lastUpdatedMillis
        )
    }

    fun formatLocalTime(instant: Instant?, zoneId: ZoneId = ZoneId.systemDefault()): String? {
        return instant?.atZone(zoneId)?.format(localTimeFormatter)
    }

    fun formatLocalTimeRange(
        start: Instant?,
        end: Instant?,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String? {
        val startText = formatLocalTime(start, zoneId) ?: return null
        val endText = formatLocalTime(end, zoneId) ?: return null
        return "$startText – $endText"
    }

    fun formatDayLength(totalSeconds: Long?): String? {
        if (totalSeconds == null || totalSeconds <= 0L) return null
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        return "${hours}h ${minutes}m"
    }

    fun formatLastUpdated(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        return Instant.ofEpochMilli(epochMillis)
            .atZone(zoneId)
            .format(updatedTimeFormatter)
    }

    fun computeCountdown(
        now: Instant,
        selectedDate: LocalDate,
        selectedTimings: SolarTimings?,
        todayTimings: SolarTimings?,
        tomorrowTimings: SolarTimings?,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): CountdownInfo {
        val today = LocalDate.now(zoneId)

        if (selectedDate.isAfter(today)) {
            val futureSunrise = selectedTimings?.sunrise
            if (futureSunrise != null && now.isBefore(futureSunrise)) {
                return buildCountdown(
                    eventType = SolarEventType.NEXT_SUNRISE,
                    now = now,
                    target = futureSunrise,
                    isSelectedDateFuture = true
                )
            }
            return CountdownInfo(
                eventType = SolarEventType.UNAVAILABLE,
                targetInstant = null,
                hours = 0L,
                minutes = 0L,
                seconds = 0L,
                isSelectedDateFuture = true
            )
        }

        val isPastDate = selectedDate.isBefore(today)
        val referenceToday = if (!isPastDate) selectedTimings ?: todayTimings else todayTimings

        val todaySunrise = referenceToday?.sunrise
        val todaySunset = referenceToday?.sunset
        val tomorrowSunrise = tomorrowTimings?.sunrise

        val (eventType, targetInstant) = when {
            todaySunrise != null && now.isBefore(todaySunrise) -> {
                SolarEventType.NEXT_SUNRISE to todaySunrise
            }
            todaySunset != null && now.isBefore(todaySunset) -> {
                SolarEventType.NEXT_SUNSET to todaySunset
            }
            tomorrowSunrise != null && now.isBefore(tomorrowSunrise) -> {
                SolarEventType.NEXT_SUNRISE to tomorrowSunrise
            }
            else -> {
                SolarEventType.UNAVAILABLE to null
            }
        }

        if (targetInstant == null) {
            return CountdownInfo(
                eventType = SolarEventType.UNAVAILABLE,
                targetInstant = null,
                hours = 0L,
                minutes = 0L,
                seconds = 0L,
                isSelectedDatePast = isPastDate
            )
        }

        return buildCountdown(
            eventType = eventType,
            now = now,
            target = targetInstant,
            isSelectedDatePast = isPastDate
        )
    }

    private fun buildCountdown(
        eventType: SolarEventType,
        now: Instant,
        target: Instant,
        isSelectedDatePast: Boolean = false,
        isSelectedDateFuture: Boolean = false
    ): CountdownInfo {
        val totalSeconds = Duration.between(now, target).seconds.coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return CountdownInfo(
            eventType = eventType,
            targetInstant = target,
            hours = hours,
            minutes = minutes,
            seconds = seconds,
            isSelectedDatePast = isSelectedDatePast,
            isSelectedDateFuture = isSelectedDateFuture
        )
    }
}
