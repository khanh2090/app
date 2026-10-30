package com.fitness.companion.presentation.workout

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.fitness.companion.domain.model.ExerciseGuide
import com.fitness.companion.domain.model.WorkoutSession
import com.fitness.companion.domain.model.WorkoutSource
import com.fitness.companion.domain.repository.ExerciseGuideRepository
import com.fitness.companion.domain.sensor.FeedbackPlayer
import com.fitness.companion.domain.usecase.SaveWorkoutSessionUseCase
import com.fitness.companion.presentation.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject

enum class WorkoutPhase { EXERCISING, RESTING, COMPLETED }

data class WorkoutPlayerUiState(
    val guide: ExerciseGuide? = null,
    val phase: WorkoutPhase = WorkoutPhase.EXERCISING,
    val currentSet: Int = 1,
    val restRemainingSeconds: Int = 0,
    val playbackError: Boolean = false,
)

/**
 * Drives a video-guided workout: plays the clip, counts the sets and runs the rest timer between
 * them. The player lives here so playback continues seamlessly across screen rotations.
 */
@HiltViewModel
class WorkoutPlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext context: Context,
    guideRepository: ExerciseGuideRepository,
    private val feedback: FeedbackPlayer,
    private val saveWorkoutSession: SaveWorkoutSessionUseCase,
) : ViewModel() {

    private val guide: ExerciseGuide? =
        guideRepository.getById(savedStateHandle.get<String>(Routes.ARG_EXERCISE_ID).orEmpty())

    private val _uiState = MutableStateFlow(WorkoutPlayerUiState(guide = guide))
    val uiState: StateFlow<WorkoutPlayerUiState> = _uiState.asStateFlow()

    /** Null only when the screen was opened with an unknown exercise id. */
    val player: ExoPlayer? = guide?.let { createGuidePlayer(context, it.videoUrl) }

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            _uiState.update { it.copy(playbackError = true) }
        }
    }

    private var restJob: Job? = null
    private var sessionStart: Instant = Instant.now()
    private var pausedByBackground = false

    init {
        player?.addListener(playerListener)
        player?.play()
    }

    /** Marks the current set as done: starts the rest timer, or finishes after the last set. */
    fun finishSet() {
        val current = _uiState.value
        val activeGuide = current.guide ?: return
        if (current.phase != WorkoutPhase.EXERCISING) return

        player?.pause()
        if (current.currentSet >= activeGuide.sets) {
            completeWorkout(activeGuide)
        } else {
            _uiState.update {
                it.copy(phase = WorkoutPhase.RESTING, restRemainingSeconds = activeGuide.restSeconds)
            }
            runRestTimer()
        }
    }

    fun skipRest() {
        if (_uiState.value.phase != WorkoutPhase.RESTING) return
        restJob?.cancel()
        startNextSet()
    }

    fun restart() {
        restJob?.cancel()
        sessionStart = Instant.now()
        _uiState.update {
            it.copy(phase = WorkoutPhase.EXERCISING, currentSet = 1, restRemainingSeconds = 0)
        }
        player?.seekTo(0)
        player?.play()
    }

    fun retryPlayback() {
        _uiState.update { it.copy(playbackError = false) }
        player?.prepare()
        player?.play()
    }

    /** The video and the rest timer stop while the app is not visible. */
    fun onBackgrounded() {
        pausedByBackground = player?.playWhenReady == true
        player?.pause()
        restJob?.cancel()
    }

    fun onForegrounded() {
        // Only resume what going to the background interrupted, not a pause the user chose.
        if (pausedByBackground) {
            pausedByBackground = false
            player?.play()
        }
        if (_uiState.value.phase == WorkoutPhase.RESTING && restJob?.isActive != true) runRestTimer()
    }

    override fun onCleared() {
        player?.removeListener(playerListener)
        player?.release()
    }

    /** Counts the rest down one second at a time, with a beep for each of the last seconds. */
    private fun runRestTimer() {
        restJob?.cancel()
        restJob = viewModelScope.launch {
            while (_uiState.value.restRemainingSeconds > 0) {
                delay(1_000)
                val remaining = _uiState.value.restRemainingSeconds - 1
                _uiState.update { it.copy(restRemainingSeconds = remaining) }
                if (remaining in 1..COUNTDOWN_CUE_SECONDS) feedback.countdownTick()
            }
            feedback.countdownFinished()
            startNextSet()
        }
    }

    private fun startNextSet() {
        _uiState.update {
            it.copy(
                phase = WorkoutPhase.EXERCISING,
                currentSet = it.currentSet + 1,
                restRemainingSeconds = 0,
            )
        }
        player?.seekTo(0)
        player?.play()
    }

    private fun completeWorkout(guide: ExerciseGuide) {
        _uiState.update { it.copy(phase = WorkoutPhase.COMPLETED) }
        val session = WorkoutSession(
            exerciseType = guide.type,
            reps = guide.sets * guide.repsPerSet,
            sets = guide.sets,
            startTime = sessionStart,
            endTime = Instant.now(),
            source = WorkoutSource.VIDEO_GUIDE,
        )
        // The save must finish even if the user leaves the screen right away.
        viewModelScope.launch { withContext(NonCancellable) { saveWorkoutSession(session) } }
    }

    private companion object {
        const val COUNTDOWN_CUE_SECONDS = 3
    }
}
