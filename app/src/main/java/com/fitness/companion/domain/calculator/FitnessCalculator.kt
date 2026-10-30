package com.fitness.companion.domain.calculator

import java.time.Duration

/** Estimates distance and energy from step counts and body measurements. */
object FitnessCalculator {

    /** Walking stride length is roughly 41.5% of body height. */
    private const val STRIDE_TO_HEIGHT_RATIO = 0.415

    /** MET of walking at a moderate 4.8 km/h pace. */
    private const val WALKING_MET = 3.5
    private const val WALKING_SPEED_KMH = 4.8
    private const val MILLIS_PER_HOUR = 3_600_000.0

    fun strideLengthMeters(heightCm: Int): Double = heightCm * STRIDE_TO_HEIGHT_RATIO / 100.0

    fun distanceMeters(steps: Int, heightCm: Int): Double = steps * strideLengthMeters(heightCm)

    /** kcal = MET x weight (kg) x hours, where hours follow from the walked distance. */
    fun stepCaloriesKcal(steps: Int, heightCm: Int, weightKg: Float): Double {
        val hours = distanceMeters(steps, heightCm) / 1000.0 / WALKING_SPEED_KMH
        return WALKING_MET * weightKg * hours
    }

    fun workoutCaloriesKcal(met: Double, weightKg: Float, duration: Duration): Double =
        met * weightKg * duration.toMillis() / MILLIS_PER_HOUR
}
