package com.example.model

import java.time.Instant

enum class SolarEventType {
    NEXT_SUNRISE,
    NEXT_SUNSET,
    UNAVAILABLE
}

data class CountdownInfo(
    val eventType: SolarEventType,
    val targetInstant: Instant?,
    val hours: Long,
    val minutes: Long,
    val seconds: Long,
    val isSelectedDatePast: Boolean = false,
    val isSelectedDateFuture: Boolean = false
)
