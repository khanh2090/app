package com.fitness.companion.domain.model

/** Body measurements and daily goals entered by the user. */
data class UserProfile(
    val heightCm: Int = 170,
    val weightKg: Float = 65f,
    val dailyStepGoal: Int = 8_000,
    val dailyCalorieGoal: Int = 400,
    val dailyActiveMinutesGoal: Int = 30,
)
