package com.fitness.companion.data.sensor

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.fitness.companion.domain.sensor.AccelerometerSource
import com.fitness.companion.domain.sensor.DeviceCapabilities
import com.fitness.companion.domain.sensor.ProximitySource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Wraps a hardware sensor in a cold flow: the listener is registered when collection starts and
 * unregistered when it is cancelled, so a paused counter draws no sensor power.
 */
private fun <T> SensorManager.sensorFlow(
    sensor: Sensor?,
    samplingPeriodUs: Int,
    transform: (SensorEvent) -> T,
): Flow<T> = callbackFlow {
    val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            trySend(transform(event))
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }
    if (sensor == null) close() else registerListener(listener, sensor, samplingPeriodUs)
    awaitClose { unregisterListener(listener) }
}

@Singleton
class ProximitySensorSource @Inject constructor(
    private val sensorManager: SensorManager,
) : ProximitySource {

    private val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    override val isAvailable: Boolean get() = sensor != null

    override fun readings(): Flow<Boolean> {
        // Many proximity sensors are binary and report either 0 or their maximum range.
        val nearBelowCm = minOf(sensor?.maximumRange ?: NEAR_DISTANCE_CM, NEAR_DISTANCE_CM)
        return sensorManager.sensorFlow(sensor, SensorManager.SENSOR_DELAY_GAME) { event ->
            event.values[0] < nearBelowCm
        }
    }

    private companion object {
        const val NEAR_DISTANCE_CM = 5f
    }
}

@Singleton
class AccelerometerSensorSource @Inject constructor(
    private val sensorManager: SensorManager,
) : AccelerometerSource {

    private val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    override val isAvailable: Boolean get() = sensor != null

    override fun readings(): Flow<Float> =
        sensorManager.sensorFlow(sensor, SensorManager.SENSOR_DELAY_GAME) { event ->
            val (x, y, z) = event.values
            sqrt(x * x + y * y + z * z)
        }
}

@Singleton
class AndroidDeviceCapabilities @Inject constructor(
    @ApplicationContext context: Context,
    sensorManager: SensorManager,
) : DeviceCapabilities {

    override val hasStepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
    override val hasProximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY) != null
    override val hasAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
    override val hasCamera = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
}
