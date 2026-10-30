package com.fitness.companion.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitness.companion.domain.model.HealthAvailability
import com.fitness.companion.domain.model.SyncOutcome
import com.fitness.companion.domain.model.UserProfile
import com.fitness.companion.domain.repository.HealthSyncRepository
import com.fitness.companion.domain.repository.UserPreferencesRepository
import com.fitness.companion.domain.usecase.SyncHealthDataUseCase
import com.fitness.companion.presentation.util.formatQuantity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Text field contents; kept as strings so partially typed numbers are not lost. */
data class ProfileForm(
    val heightCm: String = "",
    val weightKg: String = "",
    val stepGoal: String = "",
    val calorieGoal: String = "",
    val activeMinutesGoal: String = "",
) {
    val heightError: String? get() = rangeError(heightCm.toIntOrNull()?.toDouble(), 80.0, 250.0)
    val weightError: String? get() = rangeError(weightKg.replace(',', '.').toDoubleOrNull(), 20.0, 300.0)
    val stepGoalError: String? get() = rangeError(stepGoal.toIntOrNull()?.toDouble(), 100.0, 100_000.0)
    val calorieGoalError: String? get() = rangeError(calorieGoal.toIntOrNull()?.toDouble(), 10.0, 10_000.0)
    val activeMinutesError: String? get() = rangeError(activeMinutesGoal.toIntOrNull()?.toDouble(), 1.0, 1_440.0)

    val isValid: Boolean
        get() = listOf(heightError, weightError, stepGoalError, calorieGoalError, activeMinutesError)
            .all { it == null }

    /** Call only when [isValid] is true. */
    fun toProfile() = UserProfile(
        heightCm = heightCm.toInt(),
        weightKg = weightKg.replace(',', '.').toFloat(),
        dailyStepGoal = stepGoal.toInt(),
        dailyCalorieGoal = calorieGoal.toInt(),
        dailyActiveMinutesGoal = activeMinutesGoal.toInt(),
    )

    private fun rangeError(value: Double?, min: Double, max: Double): String? = when {
        value == null -> "Hãy nhập một số"
        value < min || value > max -> "Phải từ ${min.toInt()} đến ${max.toInt()}"
        else -> null
    }
}

data class ProfileUiState(
    val form: ProfileForm = ProfileForm(),
    val loaded: Boolean = false,
    val savedMessage: String? = null,
    val healthAvailability: HealthAvailability = HealthAvailability.UNSUPPORTED,
    val healthPermissionsGranted: Boolean = false,
    val syncing: Boolean = false,
    val syncMessage: String? = null,
    /** Steps Health Connect holds for today, or null when they cannot be read. */
    val healthConnectSteps: Long? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository,
    private val health: HealthSyncRepository,
    private val syncHealthData: SyncHealthDataUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    val healthPermissions: Set<String> get() = health.permissions

    init {
        viewModelScope.launch {
            val profile = preferences.userProfile.first()
            _uiState.update {
                it.copy(
                    loaded = true,
                    form = ProfileForm(
                        heightCm = profile.heightCm.toString(),
                        weightKg = profile.weightKg.toString(),
                        stepGoal = profile.dailyStepGoal.toString(),
                        calorieGoal = profile.dailyCalorieGoal.toString(),
                        activeMinutesGoal = profile.dailyActiveMinutesGoal.toString(),
                    ),
                )
            }
        }
    }

    fun updateForm(transform: (ProfileForm) -> ProfileForm) {
        _uiState.update { it.copy(form = transform(it.form), savedMessage = null) }
    }

    fun saveProfile() {
        val form = _uiState.value.form
        if (!form.isValid) return
        viewModelScope.launch {
            preferences.updateProfile(form.toProfile())
            _uiState.update { it.copy(savedMessage = "Đã lưu hồ sơ") }
        }
    }

    /** Re-reads Health Connect state; called whenever the screen becomes visible. */
    fun refreshHealth() {
        viewModelScope.launch {
            val availability = health.availability()
            val granted = try {
                health.hasAllPermissions()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            _uiState.update {
                it.copy(
                    healthAvailability = availability,
                    healthPermissionsGranted = granted,
                    healthConnectSteps = if (granted) health.readTodaySteps() else null,
                )
            }
        }
    }

    fun onHealthPermissionsResult(granted: Set<String>) {
        if (granted.containsAll(health.permissions)) {
            sync()
        } else {
            _uiState.update {
                it.copy(syncMessage = "Chưa được cấp quyền Health Connect nên không đồng bộ dữ liệu.")
            }
        }
        refreshHealth()
    }

    fun sync() {
        if (_uiState.value.syncing) return
        _uiState.update { it.copy(syncing = true, syncMessage = null) }
        viewModelScope.launch {
            val message = when (val outcome = syncHealthData()) {
                is SyncOutcome.Success ->
                    if (outcome.stepDays == 0 && outcome.workouts == 0) {
                        "Health Connect đã có dữ liệu mới nhất."
                    } else {
                        "Đã đồng bộ ${formatQuantity(outcome.stepDays, "ngày bước chân")} và " +
                            "${formatQuantity(outcome.workouts, "buổi tập")}."
                    }
                SyncOutcome.PermissionRequired -> "Hãy cấp quyền Health Connect để đồng bộ."
                SyncOutcome.Unavailable -> "Health Connect không khả dụng trên thiết bị này."
                is SyncOutcome.Failed -> "Đồng bộ thất bại: ${outcome.message ?: "lỗi không xác định"}"
            }
            _uiState.update { it.copy(syncing = false, syncMessage = message) }
            refreshHealth()
        }
    }
}
