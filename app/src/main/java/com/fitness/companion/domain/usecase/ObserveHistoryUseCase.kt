package com.fitness.companion.domain.usecase

import com.fitness.companion.domain.model.DayActivity
import com.fitness.companion.domain.model.HistoryData
import com.fitness.companion.domain.model.HistoryPeriod
import com.fitness.companion.domain.repository.StepRepository
import com.fitness.companion.domain.repository.WorkoutRepository
import com.fitness.companion.domain.util.currentDateFlow
import com.fitness.companion.domain.util.startOfDay
import com.fitness.companion.domain.util.toLocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

/** Builds the per-day step and workout totals behind the weekly and monthly charts. */
class ObserveHistoryUseCase @Inject constructor(
    private val stepRepository: StepRepository,
    private val workoutRepository: WorkoutRepository,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(period: HistoryPeriod): Flow<HistoryData> =
        currentDateFlow().flatMapLatest { today ->
            val from = today.minusDays(period.days - 1L)
            combine(
                stepRepository.observeRange(from, today),
                workoutRepository.observeBetween(from.startOfDay(), today.plusDays(1).startOfDay()),
            ) { steps, sessions ->
                val stepsByDate = steps.associateBy { it.date }
                val sessionsByDate = sessions.groupBy { it.startTime.toLocalDate() }
                val days = List(period.days) { offset ->
                    val date = from.plusDays(offset.toLong())
                    val daySessions = sessionsByDate[date].orEmpty()
                    DayActivity(
                        date = date,
                        steps = stepsByDate[date]?.steps ?: 0,
                        reps = daySessions.sumOf { it.reps },
                        workoutMinutes = (daySessions.sumOf { it.duration.toMillis() } / 60_000).toInt(),
                    )
                }
                HistoryData(days = days, sessions = sessions)
            }
        }
}
