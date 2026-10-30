package com.fitness.companion.domain.steps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class AccelerometerStepDetectorTest {

    private val detector = AccelerometerStepDetector()

    /** Feeds a sine-shaped walk sampled at 50 Hz and returns the steps counted. */
    private fun walk(seconds: Int, stepsPerSecond: Double, startMs: Long = 0): Int {
        var steps = 0
        for (i in 0 until seconds * 50) {
            val t = i / 50.0
            val magnitude = GRAVITY + 3f * sin(2 * PI * stepsPerSecond * t).toFloat()
            steps += detector.onSample(magnitude, startMs + (t * 1000).toLong())
        }
        return steps
    }

    @Test
    fun `phone lying still counts nothing`() {
        var steps = 0
        repeat(500) { steps += detector.onSample(GRAVITY, it * 20L) }

        assertEquals(0, steps)
    }

    @Test
    fun `steady walking is counted step for step`() {
        val steps = walk(seconds = 10, stepsPerSecond = 2.0)

        assertTrue("expected about 20 steps but was $steps", steps in 18..20)
    }

    @Test
    fun `a couple of shakes are not steps`() {
        val steps = walk(seconds = 1, stepsPerSecond = 2.0)

        assertEquals(0, steps)
    }

    @Test
    fun `first steps of a walk are credited once the rhythm is confirmed`() {
        // Three seconds at two steps per second is six peaks: exactly the confirmation count.
        val steps = walk(seconds = 3, stepsPerSecond = 2.0)

        assertEquals(AccelerometerStepDetector.STEPS_TO_CONFIRM, steps)
    }

    @Test
    fun `a long pause starts a new walk`() {
        walk(seconds = 5, stepsPerSecond = 2.0)

        // Two steps after a 10 s pause are below the confirmation count again.
        val afterPause = walk(seconds = 1, stepsPerSecond = 2.0, startMs = 15_000)

        assertEquals(0, afterPause)
    }

    private companion object {
        const val GRAVITY = 9.81f
    }
}
