package com.fitness.companion.domain.model

import java.time.LocalDate

/** Steps recorded on one calendar day. */
data class DailySteps(
    val date: LocalDate,
    val steps: Int,
    /** Time spent walking, estimated from step cadence. */
    val activeMillis: Long,
    /** Epoch millis of the last step recorded for this day. */
    val updatedAt: Long,
)

/** Last raw value read from the step-counter sensor, used to turn its running total into deltas. */
data class StepBaseline(
    val sensorTotal: Long,
    val epochDay: Long,
)
