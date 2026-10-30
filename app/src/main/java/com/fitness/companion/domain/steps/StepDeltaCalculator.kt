package com.fitness.companion.domain.steps

import com.fitness.companion.domain.model.StepBaseline

/**
 * Turns the step-counter sensor's running total (steps since the last boot) into the number of
 * new steps to record.
 */
object StepDeltaCalculator {

    /**
     * @param baseline last reading that was recorded, or null when there is none yet
     * @param sensorTotal current sensor value
     * @param todayEpochDay current local date as an epoch day
     * @param isFirstReadingSinceStart true for the first reading after tracking (re)started
     */
    fun compute(
        baseline: StepBaseline?,
        sensorTotal: Long,
        todayEpochDay: Long,
        isFirstReadingSinceStart: Boolean,
    ): Long {
        // First reading ever: it only establishes the baseline.
        if (baseline == null) return 0

        val sameDay = baseline.epochDay == todayEpochDay
        // Tracking was off for an unknown number of days: those steps do not belong to today.
        if (isFirstReadingSinceStart && !sameDay) return 0

        val difference = sensorTotal - baseline.sensorTotal
        return when {
            difference >= 0 -> difference
            // The counter restarted from zero because the device rebooted earlier today, so
            // everything it has counted since then is new.
            sameDay -> sensorTotal
            else -> 0
        }
    }
}
