package com.fitness.companion.domain.repository

import com.fitness.companion.domain.model.DailySteps
import com.fitness.companion.domain.model.ExerciseGuide
import com.fitness.companion.domain.model.HealthAvailability
import com.fitness.companion.domain.model.StepBaseline
import com.fitness.companion.domain.model.UserProfile
import com.fitness.companion.domain.model.WorkoutSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.LocalDate

/** Daily step history. */
interface StepRepository {
    fun observeDay(date: LocalDate): Flow<DailySteps?>
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<DailySteps>>
    suspend fun addSteps(date: LocalDate, steps: Int, activeMillis: Long)
    suspend fun getAll(): List<DailySteps>

    /** Takes days from a backup; for a day both sides have, the higher step count wins. */
    suspend fun mergeFromBackup(days: List<DailySteps>)

    /** Days whose step count changed since they were last written to Health Connect. */
    suspend fun getUnsynced(): List<DailySteps>
    suspend fun markSynced(date: LocalDate, steps: Int)
}

/** Workout history. */
interface WorkoutRepository {
    /** Sessions that started in `[from, to)`, newest first. */
    fun observeBetween(from: Instant, to: Instant): Flow<List<WorkoutSession>>
    suspend fun save(session: WorkoutSession): Long
    suspend fun getAll(): List<WorkoutSession>

    /** Adds the sessions of a backup that this device does not have yet; returns how many. */
    suspend fun mergeFromBackup(sessions: List<WorkoutSession>): Int
    suspend fun getUnsynced(): List<WorkoutSession>
    suspend fun markSynced(id: Long)
}

/** User measurements, goals and step-tracking state. */
interface UserPreferencesRepository {
    val userProfile: Flow<UserProfile>
    val stepTrackingEnabled: Flow<Boolean>
    suspend fun updateProfile(profile: UserProfile)
    suspend fun setStepTrackingEnabled(enabled: Boolean)
    suspend fun getStepBaseline(): StepBaseline?
    suspend fun setStepBaseline(baseline: StepBaseline?)
}

/** Catalogue of video-guided exercises. */
interface ExerciseGuideRepository {
    /** The catalogue: the one from the backend once loaded, the built-in one until then. */
    val guides: StateFlow<List<ExerciseGuide>>
    fun getById(id: String): ExerciseGuide?

    /** Loads the catalogue from the backend. Keeps the current one when that is not possible. */
    suspend fun refresh()

    /** The catalogue shipped inside the app. */
    fun builtIn(): List<ExerciseGuide>
}

/** Writes fitness data to Google Health Connect. */
interface HealthSyncRepository {
    /** Health Connect permissions this app asks for. */
    val permissions: Set<String>

    fun availability(): HealthAvailability
    suspend fun hasAllPermissions(): Boolean

    /** Inserts or updates one steps record per day. */
    suspend fun writeSteps(days: List<DailySteps>)
    suspend fun writeWorkout(session: WorkoutSession)

    /** Total steps Health Connect holds for today, or null when they cannot be read. */
    suspend fun readTodaySteps(): Long?
}
