package com.fitness.companion.domain.steps

import com.fitness.companion.domain.model.StepBaseline
import org.junit.Assert.assertEquals
import org.junit.Test

class StepDeltaCalculatorTest {

    @Test
    fun `first reading ever only sets the baseline`() {
        assertEquals(0, compute(baseline = null, sensorTotal = 5_000, firstReading = true))
    }

    @Test
    fun `later readings add the difference`() {
        val baseline = StepBaseline(sensorTotal = 5_000, epochDay = TODAY)

        assertEquals(120, compute(baseline, sensorTotal = 5_120, firstReading = false))
    }

    @Test
    fun `restart on the same day counts steps walked meanwhile`() {
        val baseline = StepBaseline(sensorTotal = 5_000, epochDay = TODAY)

        assertEquals(300, compute(baseline, sensorTotal = 5_300, firstReading = true))
    }

    @Test
    fun `restart on a later day ignores the gap`() {
        val baseline = StepBaseline(sensorTotal = 5_000, epochDay = TODAY - 3)

        assertEquals(0, compute(baseline, sensorTotal = 40_000, firstReading = true))
    }

    @Test
    fun `steps across midnight while tracking go to the new day`() {
        val baseline = StepBaseline(sensorTotal = 5_000, epochDay = TODAY - 1)

        assertEquals(40, compute(baseline, sensorTotal = 5_040, firstReading = false))
    }

    @Test
    fun `reboot on the same day counts everything since boot`() {
        val baseline = StepBaseline(sensorTotal = 5_000, epochDay = TODAY)

        assertEquals(250, compute(baseline, sensorTotal = 250, firstReading = true))
    }

    @Test
    fun `counter reset seen on a later day is ignored`() {
        val baseline = StepBaseline(sensorTotal = 5_000, epochDay = TODAY - 1)

        assertEquals(0, compute(baseline, sensorTotal = 250, firstReading = false))
    }

    private fun compute(baseline: StepBaseline?, sensorTotal: Long, firstReading: Boolean): Long =
        StepDeltaCalculator.compute(baseline, sensorTotal, TODAY, firstReading)

    private companion object {
        const val TODAY = 20_000L
    }
}
