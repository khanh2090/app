package com.fitness.companion.domain.counter

/**
 * Counts repetitions from accelerometer magnitude, for devices without a proximity sensor.
 *
 * The phone moves with the body (strapped to the upper arm or held against the chest). Each rep
 * starts with the body accelerating downwards (magnitude drops below gravity) and reaches the
 * bottom when the movement is stopped and reversed (magnitude rises above gravity). A rep is
 * counted on that `DOWN -> reversal` pair.
 */
class AccelerometerRepCounter(
    private val thresholdMs2: Float = DEFAULT_THRESHOLD_MS2,
    private val minRepIntervalMs: Long = DEFAULT_MIN_REP_INTERVAL_MS,
) {

    enum class State { REST, MOVING_DOWN }

    var state: State = State.REST
        private set

    var reps: Int = 0
        private set

    /** Last acceleration with gravity removed, exposed for the signal indicator. */
    var linearAcceleration: Float = 0f
        private set

    /** Low-pass estimate of gravity, so the counter works in any phone orientation. */
    private var gravity = STANDARD_GRAVITY
    private var lastRepAtMs: Long? = null

    /** @return true when this sample completes a repetition. */
    fun onSample(magnitude: Float, timestampMs: Long): Boolean {
        gravity = GRAVITY_FILTER * gravity + (1 - GRAVITY_FILTER) * magnitude
        linearAcceleration = magnitude - gravity

        when (state) {
            State.REST -> if (linearAcceleration < -thresholdMs2) state = State.MOVING_DOWN
            State.MOVING_DOWN -> if (linearAcceleration > thresholdMs2) {
                state = State.REST
                val last = lastRepAtMs
                if (last == null || timestampMs - last >= minRepIntervalMs) {
                    lastRepAtMs = timestampMs
                    reps++
                    return true
                }
            }
        }
        return false
    }

    fun resetState() {
        state = State.REST
        linearAcceleration = 0f
        gravity = STANDARD_GRAVITY
    }

    fun reset() {
        resetState()
        reps = 0
        lastRepAtMs = null
    }

    companion object {
        const val DEFAULT_THRESHOLD_MS2 = 1.5f
        const val DEFAULT_MIN_REP_INTERVAL_MS = 800L
        private const val STANDARD_GRAVITY = 9.80665f
        private const val GRAVITY_FILTER = 0.9f
    }
}
