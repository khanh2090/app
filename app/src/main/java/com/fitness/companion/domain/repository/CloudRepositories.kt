package com.fitness.companion.domain.repository

import com.fitness.companion.domain.model.DailySteps
import com.fitness.companion.domain.model.ExerciseGuide
import com.fitness.companion.domain.model.UserProfile
import com.fitness.companion.domain.model.WorkoutSession
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class UserAccount(
    val uid: String,
    val email: String,
    val displayName: String,
)

/** Everything a user owns on the backend. */
data class CloudBackup(
    val profile: UserProfile?,
    val steps: List<DailySteps>,
    val workouts: List<WorkoutSession>,
)

data class LeaderboardEntry(
    val displayName: String,
    val steps: Int,
    val isCurrentUser: Boolean,
)

/** Sign-up and sign-in against the backend. */
interface AccountRepository {
    /** False when the app was built without a backend configuration. */
    val isConfigured: Boolean

    /** The signed-in account, or null. */
    val account: Flow<UserAccount?>

    suspend fun signUp(email: String, password: String, displayName: String)
    suspend fun signIn(email: String, password: String)
    fun signOut()
}

/** Remote storage of user data, the exercise catalogue and the step leaderboard. */
interface CloudStore {
    suspend fun upload(account: UserAccount, backup: CloudBackup)
    suspend fun download(account: UserAccount): CloudBackup

    /** Publishes the user's step count of [date] to that day's leaderboard. */
    suspend fun publishSteps(account: UserAccount, date: LocalDate, steps: Int)

    /** Top step counts of [date], highest first. */
    suspend fun leaderboard(date: LocalDate, currentUid: String): List<LeaderboardEntry>

    /** Exercise catalogue stored on the backend; empty when none has been published yet. */
    suspend fun fetchGuides(): List<ExerciseGuide>
    suspend fun publishGuides(guides: List<ExerciseGuide>)
}
