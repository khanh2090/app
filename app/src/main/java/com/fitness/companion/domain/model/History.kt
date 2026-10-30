package com.fitness.companion.domain.model

import java.time.LocalDate

enum class HistoryPeriod(val days: Int, val label: String) {
    WEEK(7, "Tuần"),
    MONTH(30, "Tháng"),
}

/** Activity totals of one day, one bar in each history chart. */
data class DayActivity(
    val date: LocalDate,
    val steps: Int,
    val reps: Int,
    val workoutMinutes: Int,
)

data class HistoryData(
    /** One entry per day of the period, oldest first, including days without activity. */
    val days: List<DayActivity>,
    /** Workouts of the period, newest first. */
    val sessions: List<WorkoutSession>,
) {
    val totalSteps: Int get() = days.sumOf { it.steps }
    val averageSteps: Int get() = if (days.isEmpty()) 0 else totalSteps / days.size
    val totalReps: Int get() = days.sumOf { it.reps }
}
