package com.fitness.companion.domain.model

import java.time.Duration
import java.time.Instant

/** Where a workout session was recorded. */
enum class WorkoutSource { COUNTER, VIDEO_GUIDE }

/** One completed workout. */
data class WorkoutSession(
    val id: Long = 0,
    val exerciseType: ExerciseType,
    val reps: Int,
    val sets: Int = 1,
    val startTime: Instant,
    val endTime: Instant,
    val source: WorkoutSource,
    val syncedToHealth: Boolean = false,
) {
    val duration: Duration get() = Duration.between(startTime, endTime)
}
