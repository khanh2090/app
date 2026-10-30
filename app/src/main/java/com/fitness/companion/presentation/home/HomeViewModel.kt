package com.fitness.companion.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitness.companion.domain.model.TodaySummary
import com.fitness.companion.domain.repository.UserPreferencesRepository
import com.fitness.companion.domain.sensor.DeviceCapabilities
import com.fitness.companion.domain.usecase.ObserveTodaySummaryUseCase
import com.fitness.companion.domain.usecase.SyncHealthDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    /** Null until the first values have been read from storage. */
    val summary: TodaySummary? = null,
    val trackingEnabled: Boolean = false,
    val hasStepCounter: Boolean = true,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeTodaySummary: ObserveTodaySummaryUseCase,
    private val preferences: UserPreferencesRepository,
    capabilities: DeviceCapabilities,
    syncHealthData: SyncHealthDataUseCase,
) : ViewModel() {

    // Steps can be tracked with either sensor: the accelerometer stands in for a step counter.
    private val canCountSteps = capabilities.hasStepCounter || capabilities.hasAccelerometer

    val uiState: StateFlow<HomeUiState> = combine(
        observeTodaySummary(),
        preferences.stepTrackingEnabled,
    ) { summary, trackingEnabled ->
        HomeUiState(summary, trackingEnabled, canCountSteps)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(hasStepCounter = canCountSteps),
    )

    init {
        // Opening the app is a good moment to push anything Health Connect has not received yet.
        viewModelScope.launch { syncHealthData() }
    }

    fun setTrackingEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setStepTrackingEnabled(enabled) }
    }
}
