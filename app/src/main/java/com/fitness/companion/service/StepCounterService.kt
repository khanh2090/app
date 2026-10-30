package com.fitness.companion.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.fitness.companion.MainActivity
import com.fitness.companion.R
import com.fitness.companion.domain.model.StepBaseline
import com.fitness.companion.domain.model.TodaySummary
import com.fitness.companion.domain.repository.StepRepository
import com.fitness.companion.domain.repository.UserPreferencesRepository
import com.fitness.companion.domain.steps.AccelerometerStepDetector
import com.fitness.companion.domain.steps.StepDeltaCalculator
import com.fitness.companion.domain.usecase.ObserveTodaySummaryUseCase
import com.fitness.companion.presentation.util.APP_LOCALE
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.sqrt

/**
 * Foreground service that keeps counting steps while the app is in the background.
 *
 * It listens to the hardware step counter (`TYPE_STEP_COUNTER`), which is computed by the sensor
 * hub and batched, so tracking costs very little battery. The sensor reports the total number of
 * steps since boot; [StepDeltaCalculator] turns that into new steps, which are added to today's
 * row in Room. Distance and calories are derived from those steps and the user profile.
 */
@AndroidEntryPoint
class StepCounterService : Service(), SensorEventListener {

    @Inject lateinit var sensorManager: SensorManager
    @Inject lateinit var stepRepository: StepRepository
    @Inject lateinit var preferences: UserPreferencesRepository
    @Inject lateinit var observeTodaySummary: ObserveTodaySummaryUseCase

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Events are handled one at a time so two of them can never race on the saved baseline. */
    private val events = Channel<StepEvent>(Channel.UNLIMITED)
    private var notificationJob: Job? = null
    private var listening = false

    @Volatile private var firstReadingSinceStart = true
    private var lastStepAtMs = 0L

    // Fallback for phones whose hardware step counter exists but never reports (seen on a
    // Pixel 4): steps are detected from the accelerometer until the hardware counter proves
    // that it works.
    private val stepDetector = AccelerometerStepDetector()
    private var fallbackWakeLock: PowerManager.WakeLock? = null
    private var hardwareCounterWorks = false
    private var fallbackStepsSinceHardwareReading = 0

    private sealed interface StepEvent {
        /** Running total reported by the hardware step counter. */
        data class HardwareTotal(val total: Long) : StepEvent

        /** Steps detected from the accelerometer. */
        data class DetectedSteps(val count: Int) : StepEvent
        data object Stop : StepEvent
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        scope.launch {
            for (event in events) {
                when (event) {
                    is StepEvent.HardwareTotal -> recordReading(event.total)
                    is StepEvent.DetectedSteps -> recordDetectedSteps(event.count)
                    StepEvent.Stop -> finishStopping()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTracking()
            return START_NOT_STICKY
        }
        // The foreground notification must be posted first: the system requires it within
        // seconds of startForegroundService().
        if (!enterForeground() || !startListening()) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Sticky: if the process is killed the system recreates the service and counting resumes.
        return START_STICKY
    }

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        releaseFallbackWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            events.trySend(StepEvent.HardwareTotal(event.values[0].toLong()))
            return
        }
        val (x, y, z) = event.values
        val steps = stepDetector.onSample(sqrt(x * x + y * y + z * z), SystemClock.elapsedRealtime())
        if (steps > 0) events.trySend(StepEvent.DetectedSteps(steps))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun enterForeground(): Boolean = try {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(null), type)
        true
    } catch (e: Exception) {
        // SecurityException: the activity-recognition permission was revoked.
        // ForegroundServiceStartNotAllowedException: restarted while the app is in the background.
        Log.w(TAG, "Cannot run step tracking in the foreground", e)
        false
    }

    private fun startListening(): Boolean {
        if (listening) return true
        firstReadingSinceStart = true
        val stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        val hardwareRegistered = stepCounter != null && sensorManager.registerListener(
            this,
            stepCounter,
            SensorManager.SENSOR_DELAY_NORMAL,
            MAX_REPORT_LATENCY_US,
        )
        listening = startAccelerometerFallback() || hardwareRegistered
        if (listening) {
            notificationJob = scope.launch {
                observeTodaySummary().collect { summary ->
                    notificationManager().notify(NOTIFICATION_ID, buildNotification(summary))
                }
            }
        }
        return listening
    }

    private suspend fun recordReading(sensorTotal: Long) {
        try {
            val today = LocalDate.now()
            val delta = StepDeltaCalculator.compute(
                baseline = preferences.getStepBaseline(),
                sensorTotal = sensorTotal,
                todayEpochDay = today.toEpochDay(),
                isFirstReadingSinceStart = firstReadingSinceStart,
            )
            firstReadingSinceStart = false
            preferences.setStepBaseline(StepBaseline(sensorTotal, today.toEpochDay()))
            if (delta <= 0) return

            // Steps the fallback already stored for the same stretch are not counted twice.
            val fallbackSteps = fallbackStepsSinceHardwareReading
            val newSteps = (delta - fallbackSteps).coerceAtLeast(0)
            fallbackStepsSinceHardwareReading = 0

            // The fallback is only switched off once the hardware counter reports a real walk
            // that keeps up with what the accelerometer saw; a stray step or two is not proof.
            val keepsUp = delta >= HARDWARE_TRUST_STEPS && delta * 2 >= fallbackSteps
            if (!hardwareCounterWorks && keepsUp) {
                hardwareCounterWorks = true
                withContext(Dispatchers.Main) { stopAccelerometerFallback() }
            }
            storeSteps(today, newSteps.toInt())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store a step reading", e)
        }
    }

    private suspend fun recordDetectedSteps(count: Int) {
        if (hardwareCounterWorks) return
        try {
            fallbackStepsSinceHardwareReading += count
            storeSteps(LocalDate.now(), count)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store detected steps", e)
        }
    }

    private suspend fun storeSteps(day: LocalDate, steps: Int) {
        if (steps <= 0) return
        // Walking time: the typical duration of these steps, but never longer than the time
        // that actually passed since the previous batch.
        val now = System.currentTimeMillis()
        val typicalDuration = steps * AVERAGE_STEP_MS
        val activeMillis =
            if (lastStepAtMs == 0L) typicalDuration else minOf(typicalDuration, now - lastStepAtMs)
        lastStepAtMs = now
        stepRepository.addSteps(day, steps, activeMillis)
    }

    /**
     * Listens to the accelerometer and keeps the CPU awake, because unlike the step counter the
     * accelerometer delivers nothing while the phone sleeps in a pocket.
     */
    private fun startAccelerometerFallback(): Boolean {
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return false
        if (!sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)) {
            return false
        }
        fallbackWakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FitnessCompanion:stepFallback")
            .apply { acquire() }
        return true
    }

    private fun stopAccelerometerFallback() {
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.unregisterListener(this, it)
        }
        releaseFallbackWakeLock()
    }

    private fun releaseFallbackWakeLock() {
        fallbackWakeLock?.takeIf { it.isHeld }?.release()
        fallbackWakeLock = null
    }

    /** Stops on user request. */
    private fun stopTracking() {
        sensorManager.unregisterListener(this)
        releaseFallbackWakeLock()
        listening = false
        notificationJob?.cancel()
        // Queued behind any readings still being stored, so none of them can write the baseline
        // back after it has been cleared.
        events.trySend(StepEvent.Stop)
    }

    /** Clears the baseline: steps walked while tracking is off must not be counted later. */
    private suspend fun finishStopping() {
        preferences.setStepTrackingEnabled(false)
        preferences.setStepBaseline(null)
        withContext(Dispatchers.Main) {
            ServiceCompat.stopForeground(this@StepCounterService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun buildNotification(summary: TodaySummary?): Notification {
        val immutable = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            immutable,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, StepCounterService::class.java).setAction(ACTION_STOP),
            immutable,
        )
        val text = if (summary == null) {
            getString(R.string.step_notification_waiting)
        } else {
            String.format(
                APP_LOCALE,
                "%,d bước · %.2f km · %.0f kcal",
                summary.steps,
                summary.distanceMeters / 1000.0,
                summary.caloriesKcal,
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_steps)
            .setContentTitle(getString(R.string.step_notification_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.step_notification_stop), stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.step_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = getString(R.string.step_channel_description) }
        notificationManager().createNotificationChannel(channel)
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(NotificationManager::class.java)

    companion object {
        private const val TAG = "StepCounterService"
        private const val CHANNEL_ID = "step_tracking"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_START = "com.fitness.companion.action.START_STEP_TRACKING"
        private const val ACTION_STOP = "com.fitness.companion.action.STOP_STEP_TRACKING"

        /**
         * Lets the sensor hub deliver steps in batches of up to 1 s instead of one event per
         * step, while keeping the count on screen close to live.
         */
        private const val MAX_REPORT_LATENCY_US = 1_000_000

        /** About 100 steps per minute, a normal walking cadence. */
        private const val AVERAGE_STEP_MS = 600L

        /** Steps one hardware reading must add before the hardware counter is trusted. */
        private const val HARDWARE_TRUST_STEPS = 10

        /** Starts tracking. Call only while the app is visible and the permission is granted. */
        fun start(context: Context): Boolean = try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, StepCounterService::class.java).setAction(ACTION_START),
            )
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not start step tracking", e)
            false
        }

        fun stop(context: Context) {
            try {
                context.startService(
                    Intent(context, StepCounterService::class.java).setAction(ACTION_STOP),
                )
            } catch (e: IllegalStateException) {
                // The app is in the background and the service is not running: nothing to stop.
                Log.w(TAG, "Could not deliver the stop request", e)
            }
        }
    }
}
