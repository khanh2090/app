package com.fitness.companion.domain.model

enum class HealthAvailability {
    AVAILABLE,
    /** Health Connect exists on this device but must be installed or updated first. */
    UPDATE_REQUIRED,
    UNSUPPORTED,
}

sealed interface SyncOutcome {
    data class Success(val stepDays: Int, val workouts: Int) : SyncOutcome
    data object PermissionRequired : SyncOutcome
    data object Unavailable : SyncOutcome
    data class Failed(val message: String?) : SyncOutcome
}
