package com.fitness.companion.domain.model

/** Everything the home screen shows about the current day. */
data class TodaySummary(
    val steps: Int,
    val distanceMeters: Double,
    /** Calories from walking plus today's workouts. */
    val caloriesKcal: Double,
    /** Walking time plus workout time. */
    val activeMinutes: Int,
    val workoutCount: Int,
    val profile: UserProfile,
) {
    val stepProgress: Float get() = progress(steps.toDouble(), profile.dailyStepGoal)
    val calorieProgress: Float get() = progress(caloriesKcal, profile.dailyCalorieGoal)
    val activeProgress: Float get() = progress(activeMinutes.toDouble(), profile.dailyActiveMinutesGoal)

    private fun progress(value: Double, goal: Int): Float =
        if (goal <= 0) 0f else (value / goal).toFloat().coerceIn(0f, 1f)
}
