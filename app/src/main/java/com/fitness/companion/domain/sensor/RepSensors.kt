package com.fitness.companion.domain.sensor

import kotlinx.coroutines.flow.Flow

/** Proximity sensor readings. The sensor is only powered while the flow is collected. */
interface ProximitySource {
    val isAvailable: Boolean

    /** Emits true while something is close to the sensor. */
    fun readings(): Flow<Boolean>
}

/** Accelerometer readings. The sensor is only powered while the flow is collected. */
interface AccelerometerSource {
    val isAvailable: Boolean

    /** Emits the acceleration magnitude in m/s2, gravity included. */
    fun readings(): Flow<Float>
}

/** Hardware present on this device, used to pick fallback UI. */
interface DeviceCapabilities {
    val hasStepCounter: Boolean
    val hasProximitySensor: Boolean
    val hasAccelerometer: Boolean
    val hasCamera: Boolean
}

/** Audio and haptic cues. */
interface FeedbackPlayer {
    /** Short beep and vibration for a counted rep. */
    fun repCompleted()

    /** Tick for each of the last seconds of a rest countdown. */
    fun countdownTick()

    /** Longer tone when a rest countdown ends. */
    fun countdownFinished()
}
