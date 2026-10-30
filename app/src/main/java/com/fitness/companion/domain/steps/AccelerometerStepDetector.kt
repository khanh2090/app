package com.fitness.companion.domain.steps

/**
 * Detects walking steps from accelerometer magnitude, for phones whose hardware step counter is
 * missing or never reports.
 *
 * Each step shows up as a peak in acceleration once gravity is removed. A peak only counts when
 * it follows the previous one at a walking rhythm, and the first few steps are held back until
 * the rhythm is established, so shaking or picking up the phone is not counted.
 */
class AccelerometerStepDetector {

    /** Low-pass estimate of gravity, so the detector works in any phone orientation. */
    private var gravity = STANDARD_GRAVITY
    private var abovePeakThreshold = false
    private var lastPeakAtMs: Long? = null

    /** Consecutive peaks seen at a walking rhythm. */
    private var streak = 0

    /**
     * Feeds one accelerometer sample.
     *
     * @param magnitude acceleration magnitude in m/s2, gravity included
     * @return number of steps this sample confirms: 0 most of the time, 1 while walking, and
     * [STEPS_TO_CONFIRM] at once when a walking rhythm has just been established
     */
    fun onSample(magnitude: Float, timestampMs: Long): Int {
        gravity = GRAVITY_FILTER * gravity + (1 - GRAVITY_FILTER) * magnitude
        val linear = magnitude - gravity

        if (abovePeakThreshold) {
            // Hysteresis: the signal must fall back before another peak can start.
            if (linear < RELEASE_THRESHOLD_MS2) abovePeakThreshold = false
            return 0
        }
        if (linear < PEAK_THRESHOLD_MS2) return 0
        abovePeakThreshold = true

        val last = lastPeakAtMs
        val interval = if (last == null) null else timestampMs - last
        if (interval != null && interval < MIN_STEP_INTERVAL_MS) return 0

        lastPeakAtMs = timestampMs
        streak = if (interval == null || interval > MAX_STEP_INTERVAL_MS) 1 else streak + 1
        return when {
            streak == STEPS_TO_CONFIRM -> STEPS_TO_CONFIRM
            streak > STEPS_TO_CONFIRM -> 1
            else -> 0
        }
    }

    companion object {
        const val STEPS_TO_CONFIRM = 6
        private const val STANDARD_GRAVITY = 9.80665f
        private const val GRAVITY_FILTER = 0.9f
        private const val PEAK_THRESHOLD_MS2 = 1.0f
        private const val RELEASE_THRESHOLD_MS2 = 0.3f

        /** Faster than 4 steps per second is not walking or running. */
        private const val MIN_STEP_INTERVAL_MS = 250L

        /** A gap longer than this ends the current walk. */
        private const val MAX_STEP_INTERVAL_MS = 2_000L
    }
}
