package com.fitness.companion.domain.usecase

import com.fitness.companion.domain.calculator.FitnessCalculator
import com.fitness.companion.domain.model.DailySteps
import com.fitness.companion.domain.model.TodaySummary
import com.fitness.companion.domain.model.UserProfile
import com.fitness.companion.domain.model.WorkoutSession
import com.fitness.companion.domain.repository.StepRepository
import com.fitness.companion.domain.repository.UserPreferencesRepository
import com.fitness.companion.domain.repository.WorkoutRepository
import com.fitness.companion.domain.util.currentDateFlow
import com.fitness.companion.domain.util.startOfDay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

/** Combines today's steps, workouts and the user profile into the daily progress figures. */
class ObserveTodaySummaryUseCase @Inject constructor(
    private val stepRepository: StepRepository,
    private val workoutRepository: WorkoutRepository,
    private val preferences: UserPreferencesRepository,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<TodaySummary> = currentDateFlow().flatMapLatest { today ->
        combine(
            stepRepository.observeDay(today),
            workoutRepository.observeBetween(today.startOfDay(), today.plusDays(1).startOfDay()),
            preferences.userProfile,
            ::summarize,
        )
    }

    private fun summarize(
        daily: DailySteps?,
        workouts: List<WorkoutSession>,
        profile: UserProfile,
    ): TodaySummary {
        val steps = daily?.steps ?: 0
        val workoutCalories = workouts.sumOf {
            FitnessCalculator.workoutCaloriesKcal(it.exerciseType.met, profile.weightKg, it.duration)
        }
        val activeMillis = (daily?.activeMillis ?: 0L) + workouts.sumOf { it.duration.toMillis() }
        return TodaySummary(
            steps = steps,
            distanceMeters = FitnessCalculator.distanceMeters(steps, profile.heightCm),
            caloriesKcal = FitnessCalculator.stepCaloriesKcal(steps, profile.heightCm, profile.weightKg) +
                workoutCalories,
            activeMinutes = (activeMillis / 60_000).toInt(),
            workoutCount = workouts.size,
            profile = profile,
        )
    }
}
