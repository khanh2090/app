package com.fitness.companion.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class DailyStepsDao {

    @Query("SELECT * FROM daily_steps WHERE date = :date")
    abstract fun observe(date: String): Flow<DailyStepsEntity?>

    @Query("SELECT * FROM daily_steps WHERE date BETWEEN :from AND :to ORDER BY date")
    abstract fun observeRange(from: String, to: String): Flow<List<DailyStepsEntity>>

    @Query(
        "INSERT OR IGNORE INTO daily_steps (date, steps, activeMillis, updatedAt, syncedSteps) " +
            "VALUES (:date, 0, 0, :now, 0)",
    )
    abstract suspend fun insertIfMissing(date: String, now: Long)

    @Query(
        "UPDATE daily_steps SET steps = steps + :steps, activeMillis = activeMillis + :activeMillis, " +
            "updatedAt = :now WHERE date = :date",
    )
    abstract suspend fun increment(date: String, steps: Int, activeMillis: Long, now: Long)

    /** Adds to the day's totals, creating the row on the first step of the day. */
    @Transaction
    open suspend fun addSteps(date: String, steps: Int, activeMillis: Long, now: Long) {
        insertIfMissing(date, now)
        increment(date, steps, activeMillis, now)
    }

    @Query("SELECT * FROM daily_steps ORDER BY date")
    abstract suspend fun getAll(): List<DailyStepsEntity>

    @Query(
        "UPDATE daily_steps SET steps = :steps, activeMillis = :activeMillis, updatedAt = :updatedAt " +
            "WHERE date = :date AND steps < :steps",
    )
    abstract suspend fun raiseTo(date: String, steps: Int, activeMillis: Long, updatedAt: Long)

    /** Takes a day from a backup, keeping whichever side has counted more steps. */
    @Transaction
    open suspend fun mergeDay(date: String, steps: Int, activeMillis: Long, updatedAt: Long) {
        insertIfMissing(date, updatedAt)
        raiseTo(date, steps, activeMillis, updatedAt)
    }

    @Query("SELECT * FROM daily_steps WHERE steps > syncedSteps ORDER BY date")
    abstract suspend fun getUnsynced(): List<DailyStepsEntity>

    @Query("UPDATE daily_steps SET syncedSteps = :steps WHERE date = :date")
    abstract suspend fun markSynced(date: String, steps: Int)
}

@Dao
interface WorkoutSessionDao {

    @Insert
    suspend fun insert(entity: WorkoutSessionEntity): Long

    @Query(
        "SELECT * FROM workout_sessions WHERE startTime >= :from AND startTime < :to " +
            "ORDER BY startTime DESC",
    )
    fun observeBetween(from: Long, to: Long): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_sessions ORDER BY startTime")
    suspend fun getAll(): List<WorkoutSessionEntity>

    @Query("SELECT COUNT(*) FROM workout_sessions WHERE startTime = :startTime AND exerciseType = :exerciseType")
    suspend fun countMatching(startTime: Long, exerciseType: String): Int

    @Query("SELECT * FROM workout_sessions WHERE synced = 0 ORDER BY startTime")
    suspend fun getUnsynced(): List<WorkoutSessionEntity>

    @Query("UPDATE workout_sessions SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: Long)
}

@Database(
    entities = [DailyStepsEntity::class, WorkoutSessionEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dailyStepsDao(): DailyStepsDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
}
