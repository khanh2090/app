package com.fitness.companion.domain.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

fun LocalDate.startOfDay(zone: ZoneId = ZoneId.systemDefault()): Instant =
    atStartOfDay(zone).toInstant()

fun Instant.toLocalDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    atZone(zone).toLocalDate()

/** Emits today's date and again each time midnight passes, so "today" never goes stale. */
fun currentDateFlow(): Flow<LocalDate> = flow {
    while (true) {
        val today = LocalDate.now()
        emit(today)
        val untilMidnight = Duration.between(Instant.now(), today.plusDays(1).startOfDay())
        delay(untilMidnight.toMillis().coerceAtLeast(1_000))
    }
}.distinctUntilChanged()
