package com.fitness.companion.domain.calculator

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

class FitnessCalculatorTest {

    @Test
    fun `stride length is 41_5 percent of height`() {
        assertEquals(0.7055, FitnessCalculator.strideLengthMeters(heightCm = 170), 1e-9)
    }

    @Test
    fun `distance scales with steps and height`() {
        assertEquals(705.5, FitnessCalculator.distanceMeters(steps = 1_000, heightCm = 170), 1e-6)
        assertEquals(0.0, FitnessCalculator.distanceMeters(steps = 0, heightCm = 170), 0.0)
    }

    @Test
    fun `step calories follow MET x weight x hours`() {
        // 10 000 steps at 170 cm = 7.055 km = 1.4698 h at 4.8 km/h; 3.5 MET x 70 kg x 1.4698 h.
        val kcal = FitnessCalculator.stepCaloriesKcal(steps = 10_000, heightCm = 170, weightKg = 70f)

        assertEquals(360.1, kcal, 0.1)
    }

    @Test
    fun `heavier users burn more for the same steps`() {
        val light = FitnessCalculator.stepCaloriesKcal(steps = 5_000, heightCm = 170, weightKg = 55f)
        val heavy = FitnessCalculator.stepCaloriesKcal(steps = 5_000, heightCm = 170, weightKg = 90f)

        assertEquals(heavy / light, 90.0 / 55.0, 1e-6)
    }

    @Test
    fun `workout calories follow MET x weight x hours`() {
        val kcal = FitnessCalculator.workoutCaloriesKcal(
            met = 3.8,
            weightKg = 70f,
            duration = Duration.ofMinutes(30),
        )

        assertEquals(133.0, kcal, 1e-6)
    }
}
