package com.fitness.companion.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.fitness.companion.domain.model.DailySteps
import com.fitness.companion.domain.model.ExerciseType
import com.fitness.companion.domain.model.WorkoutSession
import com.fitness.companion.domain.model.WorkoutSource
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "daily_steps")
data class DailyStepsEntity(
    /** ISO-8601 local date, so string order equals date order. */
    @PrimaryKey val date: String,
    val steps: Int,
    val activeMillis: Long,
    val updatedAt: Long,
    /** Step count last written to Health Connect for this day. */
    val syncedSteps: Int = 0,
)

@Entity(tableName = "workout_sessions")
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseType: String,
    val reps: Int,
    val sets: Int,
    val startTime: Long,
    val endTime: Long,
    val source: String,
    val synced: Boolean,
)

fun DailyStepsEntity.toDomain() = DailySteps(
    date = LocalDate.parse(date),
    steps = steps,
    activeMillis = activeMillis,
    updatedAt = updatedAt,
)

fun WorkoutSessionEntity.toDomain() = WorkoutSession(
    id = id,
    exerciseType = ExerciseType.fromName(exerciseType) ?: ExerciseType.PUSH_UP,
    reps = reps,
    sets = sets,
    startTime = Instant.ofEpochMilli(startTime),
    endTime = Instant.ofEpochMilli(endTime),
    source = WorkoutSource.entries.firstOrNull { it.name == source } ?: WorkoutSource.COUNTER,
    syncedToHealth = synced,
)

fun WorkoutSession.toEntity() = WorkoutSessionEntity(
    id = id,
    exerciseType = exerciseType.name,
    reps = reps,
    sets = sets,
    startTime = startTime.toEpochMilli(),
    endTime = endTime.toEpochMilli(),
    source = source.name,
    synced = syncedToHealth,
)
