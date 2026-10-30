package com.fitness.companion.data.health

import android.content.Context
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.fitness.companion.domain.model.DailySteps
import com.fitness.companion.domain.model.HealthAvailability
import com.fitness.companion.domain.model.WorkoutSession
import com.fitness.companion.domain.repository.HealthSyncRepository
import com.fitness.companion.domain.util.startOfDay
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single entry point to Google Health Connect: availability, permissions, and writing the app's
 * steps and workout sessions.
 *
 * Records carry a stable client record id, so syncing the same day or session again updates the
 * existing Health Connect record instead of creating a duplicate.
 */
@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : HealthSyncRepository {

    override val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getWritePermission(StepsRecord::class),
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
    )

    private val device = Device(
        type = Device.TYPE_PHONE,
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
    )

    override fun availability(): HealthAvailability =
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.UPDATE_REQUIRED
            else -> HealthAvailability.UNSUPPORTED
        }

    // Looked up on every call because the user can install Health Connect while the app runs.
    private fun client(): HealthConnectClient {
        check(availability() == HealthAvailability.AVAILABLE) { "Health Connect is not available" }
        return HealthConnectClient.getOrCreate(context)
    }

    override suspend fun hasAllPermissions(): Boolean {
        if (availability() != HealthAvailability.AVAILABLE) return false
        return client().permissionController.getGrantedPermissions().containsAll(permissions)
    }

    override suspend fun writeSteps(days: List<DailySteps>) {
        val zone = ZoneId.systemDefault()
        val records = days.filter { it.steps > 0 }.map { day ->
            val start = day.date.startOfDay(zone)
            val end = stepsEndTime(day, zone)
            StepsRecord(
                startTime = start,
                startZoneOffset = zone.rules.getOffset(start),
                endTime = end,
                endZoneOffset = zone.rules.getOffset(end),
                count = day.steps.toLong(),
                metadata = Metadata.autoRecorded(
                    device = device,
                    clientRecordId = "steps-${day.date}",
                    // A day's count only grows, so it doubles as the record version.
                    clientRecordVersion = day.steps.toLong(),
                ),
            )
        }
        if (records.isNotEmpty()) client().insertRecords(records)
    }

    override suspend fun writeWorkout(session: WorkoutSession) {
        val zone = ZoneId.systemDefault()
        // Health Connect rejects sessions whose end is not after their start.
        val end = maxOf(session.endTime, session.startTime.plusSeconds(1))
        val record = ExerciseSessionRecord(
            startTime = session.startTime,
            startZoneOffset = zone.rules.getOffset(session.startTime),
            endTime = end,
            endZoneOffset = zone.rules.getOffset(end),
            metadata = Metadata.activelyRecorded(
                device = device,
                clientRecordId = "workout-${session.id}-${session.startTime.toEpochMilli()}",
                clientRecordVersion = 1,
            ),
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS,
            title = "${session.exerciseType.displayName} × ${session.reps}",
        )
        client().insertRecords(listOf(record))
    }

    override suspend fun readTodaySteps(): Long? {
        if (availability() != HealthAvailability.AVAILABLE) return null
        return try {
            val response = client().aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(LocalDate.now().startOfDay(), Instant.now()),
                ),
            )
            response[StepsRecord.COUNT_TOTAL] ?: 0L
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Missing read permission or a provider error: the caller shows "not available".
            null
        }
    }

    /** End of a day's steps record: its last step, kept inside that calendar day. */
    private fun stepsEndTime(day: DailySteps, zone: ZoneId): Instant {
        val start = day.date.startOfDay(zone)
        val endOfDay = day.date.plusDays(1).startOfDay(zone)
        val lastStep = Instant.ofEpochMilli(day.updatedAt)
        return maxOf(start.plusSeconds(1), minOf(lastStep, endOfDay))
    }
}
