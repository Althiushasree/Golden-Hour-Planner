package com.example.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class SolarTimings(
    val selectedDate: LocalDate,
    val sunrise: Instant?,
    val sunset: Instant?,
    val solarNoon: Instant?,
    val dayLengthSeconds: Long?,
    val civilTwilightStart: Instant?,
    val civilTwilightEnd: Instant?,
    val morningGoldenHourStart: Instant?,
    val morningGoldenHourEnd: Instant?,
    val eveningGoldenHourStart: Instant?,
    val eveningGoldenHourEnd: Instant?,
    val zoneId: ZoneId,
    val lastUpdatedMillis: Long
)
