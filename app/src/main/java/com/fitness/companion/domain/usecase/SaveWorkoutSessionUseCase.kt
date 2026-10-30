package com.fitness.companion.domain.usecase

import com.fitness.companion.domain.model.WorkoutSession
import com.fitness.companion.domain.repository.WorkoutRepository
import javax.inject.Inject

/** Stores a finished workout and forwards it to Health Connect when that is set up. */
class SaveWorkoutSessionUseCase @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val syncHealthData: SyncHealthDataUseCase,
) {

    suspend operator fun invoke(session: WorkoutSession) {
        workoutRepository.save(session)
        // Best effort: an unsynced session stays flagged and goes out with the next sync.
        syncHealthData()
    }
}
