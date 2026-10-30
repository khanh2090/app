package com.fitness.companion.domain.counter

import com.fitness.companion.domain.model.ExerciseType
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Counts repetitions from a joint angle measured by pose detection.
 *
 * A rep is the cycle `EXTENDED -> CONTRACTED -> EXTENDED`. The gap between the two thresholds is
 * hysteresis: angles in between never change the state, so jitter around one threshold cannot
 * produce extra reps.
 */
class AngleRepCounter(
    private val contractedBelow: Double,
    private val extendedAbove: Double,
    private val minRepIntervalMs: Long = ProximityRepCounter.DEFAULT_MIN_REP_INTERVAL_MS,
) {

    enum class State { UNKNOWN, EXTENDED, CONTRACTED }

    var state: State = State.UNKNOWN
        private set

    var reps: Int = 0
        private set

    private var cycleStartedExtended = false
    private var lastRepAtMs: Long? = null

    /** @return true when this angle completes a repetition. */
    fun onAngle(angleDegrees: Double, timestampMs: Long): Boolean {
        if (angleDegrees <= contractedBelow) {
            if (state == State.EXTENDED) cycleStartedExtended = true
            state = State.CONTRACTED
        } else if (angleDegrees >= extendedAbove) {
            val completed = state == State.CONTRACTED && cycleStartedExtended
            state = State.EXTENDED
            if (completed) {
                cycleStartedExtended = false
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
        state = State.UNKNOWN
        cycleStartedExtended = false
    }

    fun reset() {
        resetState()
        reps = 0
        lastRepAtMs = null
    }

    companion object {
        /**
         * Push-ups track the elbow angle (shoulder-elbow-wrist); sit-ups track the hip angle
         * (shoulder-hip-knee).
         */
        fun forExercise(type: ExerciseType): AngleRepCounter = when (type) {
            ExerciseType.SIT_UP -> AngleRepCounter(contractedBelow = 70.0, extendedAbove = 110.0)
            else -> AngleRepCounter(contractedBelow = 90.0, extendedAbove = 150.0)
        }

        /** Angle at vertex B of the triangle A-B-C, in degrees within 0..180. */
        fun angleDegrees(
            ax: Float, ay: Float,
            bx: Float, by: Float,
            cx: Float, cy: Float,
        ): Double {
            val radians = atan2((cy - by).toDouble(), (cx - bx).toDouble()) -
                atan2((ay - by).toDouble(), (ax - bx).toDouble())
            val degrees = abs(Math.toDegrees(radians))
            return if (degrees > 180.0) 360.0 - degrees else degrees
        }
    }
}
