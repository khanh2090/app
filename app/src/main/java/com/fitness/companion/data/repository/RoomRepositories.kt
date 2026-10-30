package com.fitness.companion.data.repository

import com.fitness.companion.data.local.DailyStepsDao
import com.fitness.companion.data.local.DailyStepsEntity
import com.fitness.companion.data.local.WorkoutSessionDao
import com.fitness.companion.data.local.WorkoutSessionEntity
import com.fitness.companion.data.local.toDomain
import com.fitness.companion.data.local.toEntity
import com.fitness.companion.domain.model.DailySteps
import com.fitness.companion.domain.model.WorkoutSession
import com.fitness.companion.domain.repository.StepRepository
import com.fitness.companion.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StepRepositoryImpl @Inject constructor(
    private val dao: DailyStepsDao,
) : StepRepository {

    override fun observeDay(date: LocalDate): Flow<DailySteps?> =
        dao.observe(date.toString()).map { it?.toDomain() }

    override fun observeRange(from: LocalDate, to: LocalDate): Flow<List<DailySteps>> =
        dao.observeRange(from.toString(), to.toString()).map { it.map(DailyStepsEntity::toDomain) }

    override suspend fun addSteps(date: LocalDate, steps: Int, activeMillis: Long) =
        dao.addSteps(date.toString(), steps, activeMillis, System.currentTimeMillis())

    override suspend fun getAll(): List<DailySteps> = dao.getAll().map(DailyStepsEntity::toDomain)

    override suspend fun mergeFromBackup(days: List<DailySteps>) {
        days.forEach { dao.mergeDay(it.date.toString(), it.steps, it.activeMillis, it.updatedAt) }
    }

    override suspend fun getUnsynced(): List<DailySteps> =
        dao.getUnsynced().map(DailyStepsEntity::toDomain)

    override suspend fun markSynced(date: LocalDate, steps: Int) =
        dao.markSynced(date.toString(), steps)
}

@Singleton
class WorkoutRepositoryImpl @Inject constructor(
    private val dao: WorkoutSessionDao,
) : WorkoutRepository {

    override fun observeBetween(from: Instant, to: Instant): Flow<List<WorkoutSession>> =
        dao.observeBetween(from.toEpochMilli(), to.toEpochMilli())
            .map { it.map(WorkoutSessionEntity::toDomain) }

    override suspend fun save(session: WorkoutSession): Long = dao.insert(session.toEntity())

    override suspend fun getAll(): List<WorkoutSession> =
        dao.getAll().map(WorkoutSessionEntity::toDomain)

    override suspend fun mergeFromBackup(sessions: List<WorkoutSession>): Int {
        var added = 0
        sessions.forEach { session ->
            val known = dao.countMatching(session.startTime.toEpochMilli(), session.exerciseType.name) > 0
            if (!known) {
                dao.insert(session.copy(id = 0, syncedToHealth = false).toEntity())
                added++
            }
        }
        return added
    }

    override suspend fun getUnsynced(): List<WorkoutSession> =
        dao.getUnsynced().map(WorkoutSessionEntity::toDomain)

    override suspend fun markSynced(id: Long) = dao.markSynced(id)
}
