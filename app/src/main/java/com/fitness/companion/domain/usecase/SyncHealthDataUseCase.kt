package com.fitness.companion.domain.usecase

import com.fitness.companion.domain.model.HealthAvailability
import com.fitness.companion.domain.model.SyncOutcome
import com.fitness.companion.domain.repository.HealthSyncRepository
import com.fitness.companion.domain.repository.StepRepository
import com.fitness.companion.domain.repository.WorkoutRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/** Pushes every step day and workout that Health Connect does not have yet. */
class SyncHealthDataUseCase @Inject constructor(
    private val health: HealthSyncRepository,
    private val stepRepository: StepRepository,
    private val workoutRepository: WorkoutRepository,
) {

    suspend operator fun invoke(): SyncOutcome {
        if (health.availability() != HealthAvailability.AVAILABLE) return SyncOutcome.Unavailable
        return try {
            if (!health.hasAllPermissions()) return SyncOutcome.PermissionRequired

            val days = stepRepository.getUnsynced()
            if (days.isNotEmpty()) {
                health.writeSteps(days)
                days.forEach { stepRepository.markSynced(it.date, it.steps) }
            }

            val sessions = workoutRepository.getUnsynced()
            sessions.forEach { session ->
                health.writeWorkout(session)
                workoutRepository.markSynced(session.id)
            }
            SyncOutcome.Success(stepDays = days.size, workouts = sessions.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SyncOutcome.Failed(e.message)
        }
    }
}
