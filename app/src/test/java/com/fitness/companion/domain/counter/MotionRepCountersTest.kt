package com.fitness.companion.domain.counter

import com.fitness.companion.domain.model.ExerciseType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccelerometerRepCounterTest {

    private val counter = AccelerometerRepCounter(thresholdMs2 = 1.5f, minRepIntervalMs = 800)

    @Test
    fun `holding still counts nothing`() {
        repeat(50) { index -> assertFalse(counter.onSample(GRAVITY, index * 20L)) }

        assertEquals(0, counter.reps)
    }

    @Test
    fun `downward dip followed by reversal counts one rep`() {
        assertFalse(counter.onSample(GRAVITY, 0))
        assertFalse(counter.onSample(DIP, 100))
        assertEquals(AccelerometerRepCounter.State.MOVING_DOWN, counter.state)
        assertTrue(counter.onSample(SPIKE, 400))

        assertEquals(1, counter.reps)
        assertEquals(AccelerometerRepCounter.State.REST, counter.state)
    }

    @Test
    fun `second cycle inside the debounce window is ignored`() {
        counter.onSample(DIP, 0)
        assertTrue(counter.onSample(SPIKE, 300))

        counter.onSample(DIP, 500)
        assertFalse(counter.onSample(SPIKE, 700))
        assertEquals(1, counter.reps)

        counter.onSample(DIP, 1000)
        assertTrue(counter.onSample(SPIKE, 1300))
        assertEquals(2, counter.reps)
    }

    private companion object {
        const val GRAVITY = 9.81f
        const val DIP = 6.0f
        const val SPIKE = 13.5f
    }
}

class AngleRepCounterTest {

    private val counter = AngleRepCounter(contractedBelow = 90.0, extendedAbove = 150.0)

    @Test
    fun `extended contracted extended counts one rep`() {
        assertFalse(counter.onAngle(170.0, 0))
        assertFalse(counter.onAngle(80.0, 500))
        assertTrue(counter.onAngle(165.0, 1000))

        assertEquals(1, counter.reps)
    }

    @Test
    fun `cycle that starts contracted is not a rep`() {
        assertFalse(counter.onAngle(70.0, 0))
        assertFalse(counter.onAngle(170.0, 600))

        assertEquals(0, counter.reps)
    }

    @Test
    fun `angles between the thresholds never change the state`() {
        counter.onAngle(170.0, 0)
        // Half a push-up: the elbow never reaches the contracted threshold.
        assertFalse(counter.onAngle(120.0, 400))
        assertFalse(counter.onAngle(170.0, 800))

        assertEquals(0, counter.reps)
        assertEquals(AngleRepCounter.State.EXTENDED, counter.state)
    }

    @Test
    fun `sit-up counter uses the hip angle thresholds`() {
        val sitUps = AngleRepCounter.forExercise(ExerciseType.SIT_UP)

        sitUps.onAngle(130.0, 0)
        sitUps.onAngle(60.0, 700)
        assertTrue(sitUps.onAngle(125.0, 1400))
    }

    @Test
    fun `angleDegrees measures the angle at the middle point`() {
        // Right angle at the origin.
        assertEquals(90.0, AngleRepCounter.angleDegrees(0f, 1f, 0f, 0f, 1f, 0f), 0.001)
        // Three points on one line.
        assertEquals(180.0, AngleRepCounter.angleDegrees(-1f, 0f, 0f, 0f, 1f, 0f), 0.001)
        assertEquals(135.0, AngleRepCounter.angleDegrees(-1f, -1f, 0f, 0f, 1f, 0f), 0.001)
        // A 270 degree sweep is reported as its 90 degree inner angle.
        assertEquals(90.0, AngleRepCounter.angleDegrees(-1f, 1f, 0f, 0f, -1f, -1f), 0.001)
    }
}
