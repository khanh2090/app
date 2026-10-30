package com.fitness.companion.domain.counter

/**
 * Counts repetitions from a proximity sensor with a small state machine.
 *
 * The phone lies under the chest (push-ups) or near the knees (sit-ups), so the body covers the
 * sensor once per repetition. One valid rep is the full cycle `FAR -> NEAR -> FAR`:
 *
 * ```
 *   IDLE --far--> FAR --near--> NEAR --far--> FAR (+1 rep)
 *   IDLE --near-> NEAR --far--> FAR (no rep: the cycle did not start from FAR)
 * ```
 *
 * A cycle that completes less than [minRepIntervalMs] after the previous rep is treated as sensor
 * bounce and ignored.
 *
 * The class holds no Android dependency: feed it readings from any source and a clock.
 */
class ProximityRepCounter(
    private val minRepIntervalMs: Long = DEFAULT_MIN_REP_INTERVAL_MS,
) {

    enum class State { IDLE, FAR, NEAR }

    var state: State = State.IDLE
        private set

    var reps: Int = 0
        private set

    /** True once a FAR -> NEAR transition was seen, i.e. the rep started from the top position. */
    private var cycleStartedFromFar = false
    private var lastRepAtMs: Long? = null

    /**
     * Feeds one sensor reading.
     *
     * @return true when this reading completes a valid repetition.
     */
    fun onReading(isNear: Boolean, timestampMs: Long): Boolean {
        val previous = state
        state = if (isNear) State.NEAR else State.FAR

        if (previous == State.FAR && state == State.NEAR) {
            cycleStartedFromFar = true
            return false
        }
        if (previous == State.NEAR && state == State.FAR && cycleStartedFromFar) {
            cycleStartedFromFar = false
            val last = lastRepAtMs
            if (last != null && timestampMs - last < minRepIntervalMs) return false
            lastRepAtMs = timestampMs
            reps++
            return true
        }
        return false
    }

    /** Forgets the sensor state but keeps the rep count, used when counting is paused. */
    fun resetState() {
        state = State.IDLE
        cycleStartedFromFar = false
    }

    fun reset() {
        resetState()
        reps = 0
        lastRepAtMs = null
    }

    companion object {
        const val DEFAULT_MIN_REP_INTERVAL_MS = 500L
    }
}
