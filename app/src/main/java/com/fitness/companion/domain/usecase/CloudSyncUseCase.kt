package com.fitness.companion.domain.usecase

import com.fitness.companion.domain.model.UserProfile
import com.fitness.companion.domain.repository.CloudBackup
import com.fitness.companion.domain.repository.CloudStore
import com.fitness.companion.domain.repository.ExerciseGuideRepository
import com.fitness.companion.domain.repository.StepRepository
import com.fitness.companion.domain.repository.UserAccount
import com.fitness.companion.domain.repository.UserPreferencesRepository
import com.fitness.companion.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject

data class CloudSyncResult(
    val uploadedDays: Int,
    val uploadedWorkouts: Int,
    val restoredWorkouts: Int,
)

/**
 * Two-way sync with the backend: first pulls what the account already has (so a new phone gets
 * its history back), then pushes everything stored on this device.
 */
class CloudSyncUseCase @Inject constructor(
    private val cloudStore: CloudStore,
    private val stepRepository: StepRepository,
    private val workoutRepository: WorkoutRepository,
    private val preferences: UserPreferencesRepository,
    private val guideRepository: ExerciseGuideRepository,
) {

    suspend operator fun invoke(account: UserAccount): CloudSyncResult {
        val remote = cloudStore.download(account)
        stepRepository.mergeFromBackup(remote.steps)
        val restoredWorkouts = workoutRepository.mergeFromBackup(remote.workouts)

        // A device that still has the default profile takes the one saved in the account.
        var profile = preferences.userProfile.first()
        val remoteProfile = remote.profile
        if (profile == UserProfile() && remoteProfile != null) {
            preferences.updateProfile(remoteProfile)
            profile = remoteProfile
        }

        val steps = stepRepository.getAll()
        val workouts = workoutRepository.getAll()
        cloudStore.upload(account, CloudBackup(profile, steps, workouts))

        val today = LocalDate.now()
        cloudStore.publishSteps(account, today, steps.firstOrNull { it.date == today }?.steps ?: 0)

        // The first account to sync publishes the built-in exercise catalogue to the backend.
        if (cloudStore.fetchGuides().isEmpty()) cloudStore.publishGuides(guideRepository.builtIn())
        guideRepository.refresh()

        return CloudSyncResult(steps.size, workouts.size, restoredWorkouts)
    }
}
