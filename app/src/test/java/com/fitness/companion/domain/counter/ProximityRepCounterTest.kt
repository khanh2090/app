package com.fitness.companion.domain.counter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProximityRepCounterTest {

    private val counter = ProximityRepCounter(minRepIntervalMs = 500)

    @Test
    fun `far near far counts one rep`() {
        assertFalse(counter.onReading(isNear = false, timestampMs = 0))
        assertFalse(counter.onReading(isNear = true, timestampMs = 400))
        assertTrue(counter.onReading(isNear = false, timestampMs = 900))

        assertEquals(1, counter.reps)
        assertEquals(ProximityRepCounter.State.FAR, counter.state)
    }

    @Test
    fun `cycle that starts near is not a rep`() {
        assertFalse(counter.onReading(isNear = true, timestampMs = 0))
        assertFalse(counter.onReading(isNear = false, timestampMs = 600))

        assertEquals(0, counter.reps)
    }

    @Test
    fun `repeated identical readings do not count`() {
        counter.onReading(isNear = false, timestampMs = 0)
        counter.onReading(isNear = true, timestampMs = 100)
        counter.onReading(isNear = true, timestampMs = 200)
        counter.onReading(isNear = false, timestampMs = 700)
        counter.onReading(isNear = false, timestampMs = 800)

        assertEquals(1, counter.reps)
    }

    @Test
    fun `cycle finishing within the debounce window is ignored`() {
        counter.onReading(isNear = false, timestampMs = 0)
        counter.onReading(isNear = true, timestampMs = 300)
        assertTrue(counter.onReading(isNear = false, timestampMs = 600))

        // Sensor bounce: a second cycle 200 ms after the counted rep.
        counter.onReading(isNear = true, timestampMs = 700)
        assertFalse(counter.onReading(isNear = false, timestampMs = 800))
        assertEquals(1, counter.reps)

        // A real second rep, 600 ms after the first.
        counter.onReading(isNear = true, timestampMs = 1000)
        assertTrue(counter.onReading(isNear = false, timestampMs = 1200))
        assertEquals(2, counter.reps)
    }

    @Test
    fun `resetState keeps the count but requires a fresh cycle`() {
        counter.onReading(isNear = false, timestampMs = 0)
        counter.onReading(isNear = true, timestampMs = 300)
        counter.onReading(isNear = false, timestampMs = 600)
        counter.onReading(isNear = true, timestampMs = 1500)

        counter.resetState()

        assertEquals(1, counter.reps)
        assertEquals(ProximityRepCounter.State.IDLE, counter.state)
        // The half-finished cycle from before the pause must not complete a rep.
        assertFalse(counter.onReading(isNear = false, timestampMs = 3000))
        assertEquals(1, counter.reps)
    }

    @Test
    fun `reset clears the count`() {
        counter.onReading(isNear = false, timestampMs = 0)
        counter.onReading(isNear = true, timestampMs = 300)
        counter.onReading(isNear = false, timestampMs = 600)

        counter.reset()

        assertEquals(0, counter.reps)
    }
}
