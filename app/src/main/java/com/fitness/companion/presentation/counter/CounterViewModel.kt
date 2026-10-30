package com.fitness.companion.presentation.counter

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitness.companion.domain.counter.AccelerometerRepCounter
import com.fitness.companion.domain.counter.AngleRepCounter
import com.fitness.companion.domain.counter.ProximityRepCounter
import com.fitness.companion.domain.model.ExerciseType
import com.fitness.companion.domain.model.WorkoutSession
import com.fitness.companion.domain.model.WorkoutSource
import com.fitness.companion.domain.sensor.AccelerometerSource
import com.fitness.companion.domain.sensor.DeviceCapabilities
import com.fitness.companion.domain.sensor.FeedbackPlayer
import com.fitness.companion.domain.sensor.ProximitySource
import com.fitness.companion.domain.usecase.SaveWorkoutSessionUseCase
import com.fitness.companion.presentation.util.formatDuration
import com.fitness.companion.presentation.util.formatQuantity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import kotlin.math.abs

enum class CounterMode(val label: String) {
    PROXIMITY("Tiệm cận"),
    ACCELEROMETER("Chuyển động"),
    CAMERA("Camera AI"),
    MANUAL("Thủ công"),
}

enum class CounterStatus { IDLE, RUNNING, PAUSED }

enum class SignalLevel { OFF, WAITING, ACTIVE }

/** What the sensor currently sees, shown as a coloured dot with a label. */
data class SensorSignal(val level: SignalLevel, val label: String)

data class CounterUiState(
    val exercise: ExerciseType,
    val mode: CounterMode,
    /** Modes this device's hardware supports; manual counting is always among them. */
    val availableModes: List<CounterMode>,
    /** Explains which sensors are missing, or null when the device has all of them. */
    val fallbackNotice: String?,
    val status: CounterStatus = CounterStatus.IDLE,
    val reps: Int = 0,
    val elapsedMs: Long = 0,
    val signal: SensorSignal = IDLE_SIGNAL,
    val lastResult: String? = null,
) {
    companion object {
        val IDLE_SIGNAL = SensorSignal(SignalLevel.OFF, "Cảm biến đang nghỉ")
    }
}

@HiltViewModel
class CounterViewModel @Inject constructor(
    private val proximitySource: ProximitySource,
    private val accelerometerSource: AccelerometerSource,
    capabilities: DeviceCapabilities,
    private val feedback: FeedbackPlayer,
    private val saveWorkoutSession: SaveWorkoutSessionUseCase,
) : ViewModel() {

    private val proximityCounter = ProximityRepCounter()
    private val accelerometerCounter = AccelerometerRepCounter()
    private var angleCounter: AngleRepCounter

    private var sensorJob: Job? = null
    private var tickerJob: Job? = null
    private var sessionStart: Instant? = null

    /** Counting time before the current run, i.e. excluding pauses. */
    private var accumulatedMs = 0L
    private var resumedAtMs = 0L

    private val _uiState: MutableStateFlow<CounterUiState>
    val uiState: StateFlow<CounterUiState>

    init {
        val exercise = ExerciseType.counterTypes.first()
        val modes = buildList {
            if (capabilities.hasProximitySensor) add(CounterMode.PROXIMITY)
            if (capabilities.hasAccelerometer) add(CounterMode.ACCELEROMETER)
            if (capabilities.hasCamera) add(CounterMode.CAMERA)
            add(CounterMode.MANUAL)
        }
        angleCounter = AngleRepCounter.forExercise(exercise)
        _uiState = MutableStateFlow(
            CounterUiState(
                exercise = exercise,
                mode = modes.first(),
                availableModes = modes,
                fallbackNotice = fallbackNotice(capabilities),
            ),
        )
        uiState = _uiState.asStateFlow()
    }

    fun selectExercise(exercise: ExerciseType) {
        if (_uiState.value.status != CounterStatus.IDLE) return
        angleCounter = AngleRepCounter.forExercise(exercise)
        _uiState.update { it.copy(exercise = exercise, lastResult = null) }
    }

    fun selectMode(mode: CounterMode) {
        val current = _uiState.value
        if (current.status != CounterStatus.IDLE || mode !in current.availableModes) return
        _uiState.update { it.copy(mode = mode, lastResult = null) }
    }

    fun start() {
        if (_uiState.value.status != CounterStatus.IDLE) return
        proximityCounter.reset()
        accelerometerCounter.reset()
        angleCounter.reset()
        sessionStart = Instant.now()
        accumulatedMs = 0
        _uiState.update { it.copy(reps = 0, elapsedMs = 0, lastResult = null) }
        run()
    }

    fun pause() {
        if (_uiState.value.status != CounterStatus.RUNNING) return
        accumulatedMs = elapsedNow()
        stopSensing()
        _uiState.update {
            it.copy(
                status = CounterStatus.PAUSED,
                elapsedMs = accumulatedMs,
                signal = CounterUiState.IDLE_SIGNAL,
            )
        }
    }

    fun resume() {
        if (_uiState.value.status != CounterStatus.PAUSED) return
        // The body may have moved during the pause, so each state machine starts over.
        proximityCounter.resetState()
        accelerometerCounter.resetState()
        angleCounter.resetState()
        run()
    }

    /** Ends the session and stores it when at least one rep was counted. */
    fun stop() {
        val current = _uiState.value
        if (current.status == CounterStatus.IDLE) return
        val activeMs = if (current.status == CounterStatus.RUNNING) elapsedNow() else accumulatedMs
        stopSensing()

        val startedAt = sessionStart
        val result = if (current.reps > 0 && startedAt != null) {
            val session = WorkoutSession(
                exerciseType = current.exercise,
                reps = current.reps,
                startTime = startedAt,
                endTime = startedAt.plusMillis(activeMs.coerceAtLeast(1_000)),
                source = WorkoutSource.COUNTER,
            )
            // The save must finish even if the user leaves the screen right after stopping.
            viewModelScope.launch { withContext(NonCancellable) { saveWorkoutSession(session) } }
            "Đã lưu ${formatQuantity(current.reps, "lần")} ${current.exercise.displayName.lowercase()} " +
                "trong ${formatDuration(activeMs)}"
        } else {
            "Chưa đếm được lần nào nên không lưu"
        }
        sessionStart = null
        _uiState.update {
            it.copy(
                status = CounterStatus.IDLE,
                reps = 0,
                elapsedMs = 0,
                signal = CounterUiState.IDLE_SIGNAL,
                lastResult = result,
            )
        }
    }

    /** Sensors and the timer stop while the app is not visible. */
    fun onAppBackgrounded() = pause()

    /** A tap on the counter in manual mode. */
    fun onManualRep() {
        val current = _uiState.value
        if (current.status == CounterStatus.RUNNING && current.mode == CounterMode.MANUAL) registerRep()
    }

    /** Joint angle from pose detection, or null when no body is visible in the camera image. */
    fun onPoseAngle(angleDegrees: Double?) {
        val current = _uiState.value
        if (current.status != CounterStatus.RUNNING || current.mode != CounterMode.CAMERA) return
        if (angleDegrees == null) {
            setSignal(SignalLevel.WAITING, "Không thấy cơ thể")
            return
        }
        setSignal(SignalLevel.ACTIVE, "Đã thấy cơ thể · góc khớp ${angleDegrees.toInt()}°")
        if (angleCounter.onAngle(angleDegrees, SystemClock.elapsedRealtime())) registerRep()
    }

    override fun onCleared() {
        stopSensing()
    }

    private fun run() {
        resumedAtMs = SystemClock.elapsedRealtime()
        _uiState.update { it.copy(status = CounterStatus.RUNNING) }
        tickerJob = viewModelScope.launch {
            while (isActive) {
                delay(TICK_MS)
                _uiState.update { it.copy(elapsedMs = elapsedNow()) }
            }
        }
        when (_uiState.value.mode) {
            CounterMode.PROXIMITY -> {
                setSignal(SignalLevel.WAITING, "Đang chờ cảm biến")
                sensorJob = viewModelScope.launch {
                    proximitySource.readings().collect { onProximityReading(it) }
                }
            }
            CounterMode.ACCELEROMETER -> {
                setSignal(SignalLevel.WAITING, "Đang chờ chuyển động")
                sensorJob = viewModelScope.launch {
                    accelerometerSource.readings().collect { onAccelerometerSample(it) }
                }
            }
            CounterMode.CAMERA -> setSignal(SignalLevel.WAITING, "Đang tìm cơ thể bạn")
            CounterMode.MANUAL -> setSignal(SignalLevel.ACTIVE, "Chạm vào số đếm sau mỗi lần tập")
        }
    }

    private fun onProximityReading(isNear: Boolean) {
        if (isNear) {
            setSignal(SignalLevel.ACTIVE, "GẦN · phát hiện cơ thể")
        } else {
            setSignal(SignalLevel.WAITING, "XA · cảm biến trống")
        }
        if (proximityCounter.onReading(isNear, SystemClock.elapsedRealtime())) registerRep()
    }

    private fun onAccelerometerSample(magnitude: Float) {
        val counted = accelerometerCounter.onSample(magnitude, SystemClock.elapsedRealtime())
        if (abs(accelerometerCounter.linearAcceleration) > MOTION_SIGNAL_MS2) {
            setSignal(SignalLevel.ACTIVE, "Phát hiện chuyển động")
        } else {
            setSignal(SignalLevel.WAITING, "Đang đứng yên")
        }
        if (counted) registerRep()
    }

    private fun registerRep() {
        feedback.repCompleted()
        _uiState.update { it.copy(reps = it.reps + 1) }
    }

    private fun stopSensing() {
        sensorJob?.cancel()
        sensorJob = null
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun setSignal(level: SignalLevel, label: String) {
        // Equal values are not re-emitted, so a 50 Hz sensor does not recompose the screen.
        _uiState.update { it.copy(signal = SensorSignal(level, label)) }
    }

    private fun elapsedNow(): Long = accumulatedMs + (SystemClock.elapsedRealtime() - resumedAtMs)

    private fun fallbackNotice(capabilities: DeviceCapabilities): String? {
        val missing = buildList {
            if (!capabilities.hasProximitySensor) add("cảm biến tiệm cận")
            if (!capabilities.hasAccelerometer) add("gia tốc kế")
            if (!capabilities.hasCamera) add("camera")
        }
        return if (missing.isEmpty()) {
            null
        } else {
            "Thiết bị này không có ${missing.joinToString(", ")}. Các chế độ đếm tương ứng " +
                "đã được ẩn; những chế độ còn lại vẫn hoạt động bình thường."
        }
    }

    private companion object {
        const val TICK_MS = 250L
        const val MOTION_SIGNAL_MS2 = 0.8f
    }
}
