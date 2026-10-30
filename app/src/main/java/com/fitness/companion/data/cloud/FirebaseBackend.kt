package com.fitness.companion.data.cloud

import android.content.Context
import com.fitness.companion.domain.model.DailySteps
import com.fitness.companion.domain.model.Difficulty
import com.fitness.companion.domain.model.ExerciseGuide
import com.fitness.companion.domain.model.ExerciseType
import com.fitness.companion.domain.model.UserProfile
import com.fitness.companion.domain.model.WorkoutSession
import com.fitness.companion.domain.model.WorkoutSource
import com.fitness.companion.domain.repository.AccountRepository
import com.fitness.companion.domain.repository.CloudBackup
import com.fitness.companion.domain.repository.CloudStore
import com.fitness.companion.domain.repository.LeaderboardEntry
import com.fitness.companion.domain.repository.UserAccount
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tells whether a Firebase project is attached to this build. The configuration comes from
 * `app/google-services.json`; a build without that file has no default Firebase app, and every
 * backend feature must stay off instead of crashing.
 */
@Singleton
class FirebaseConfig @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val isConfigured: Boolean get() = FirebaseApp.getApps(context).isNotEmpty()

    fun requireConfigured() = check(isConfigured) { "Backend chưa được cấu hình" }
}

/** Email and password accounts with Firebase Authentication. */
@Singleton
class FirebaseAccountRepository @Inject constructor(
    private val config: FirebaseConfig,
) : AccountRepository {

    override val isConfigured: Boolean get() = config.isConfigured

    private fun auth(): FirebaseAuth {
        config.requireConfigured()
        return FirebaseAuth.getInstance()
    }

    override val account: Flow<UserAccount?>
        get() = if (!config.isConfigured) {
            flowOf(null)
        } else {
            callbackFlow {
                val auth = auth()
                val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.toAccount()) }
                auth.addAuthStateListener(listener)
                awaitClose { auth.removeAuthStateListener(listener) }
            }
        }

    override suspend fun signUp(email: String, password: String, displayName: String) {
        val user = auth().createUserWithEmailAndPassword(email, password).await().user ?: return
        user.updateProfile(
            UserProfileChangeRequest.Builder().setDisplayName(displayName).build(),
        ).await()
    }

    override suspend fun signIn(email: String, password: String) {
        auth().signInWithEmailAndPassword(email, password).await()
    }

    override fun signOut() {
        if (config.isConfigured) auth().signOut()
    }

    private fun FirebaseUser.toAccount() = UserAccount(
        uid = uid,
        email = email.orEmpty(),
        // The name is set right after sign-up; until then the part before "@" stands in.
        displayName = displayName?.takeIf { it.isNotBlank() } ?: email.orEmpty().substringBefore('@'),
    )
}

/**
 * Cloud Firestore layout:
 *
 * ```
 * users/{uid}                         profile fields
 * users/{uid}/dailySteps/{date}       steps, activeMillis, updatedAt
 * users/{uid}/workouts/{startMillis}  one finished workout
 * leaderboard/{date}/entries/{uid}    displayName, steps
 * guides/{id}                         exercise catalogue
 * ```
 */
@Singleton
class FirestoreCloudStore @Inject constructor(
    private val config: FirebaseConfig,
) : CloudStore {

    private fun db(): FirebaseFirestore {
        config.requireConfigured()
        return FirebaseFirestore.getInstance()
    }

    override suspend fun upload(account: UserAccount, backup: CloudBackup) {
        val db = db()
        val user = db.collection(USERS).document(account.uid)

        val writes = buildList<Pair<com.google.firebase.firestore.DocumentReference, Map<String, Any>>> {
            val profile = buildMap<String, Any> {
                put("displayName", account.displayName)
                backup.profile?.let {
                    put("heightCm", it.heightCm)
                    put("weightKg", it.weightKg.toDouble())
                    put("dailyStepGoal", it.dailyStepGoal)
                    put("dailyCalorieGoal", it.dailyCalorieGoal)
                    put("dailyActiveMinutesGoal", it.dailyActiveMinutesGoal)
                }
            }
            add(user to profile)
            backup.steps.forEach { day ->
                add(
                    user.collection(DAILY_STEPS).document(day.date.toString()) to mapOf(
                        "steps" to day.steps,
                        "activeMillis" to day.activeMillis,
                        "updatedAt" to day.updatedAt,
                    ),
                )
            }
            backup.workouts.forEach { session ->
                // The start time identifies a workout, so uploading twice never duplicates it.
                add(
                    user.collection(WORKOUTS).document(session.startTime.toEpochMilli().toString()) to mapOf(
                        "exerciseType" to session.exerciseType.name,
                        "reps" to session.reps,
                        "sets" to session.sets,
                        "startTime" to session.startTime.toEpochMilli(),
                        "endTime" to session.endTime.toEpochMilli(),
                        "source" to session.source.name,
                    ),
                )
            }
        }
        // Firestore accepts at most 500 writes per batch.
        writes.chunked(MAX_BATCH_WRITES).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { (document, data) -> batch.set(document, data, SetOptions.merge()) }
            batch.commit().await()
        }
    }

    override suspend fun download(account: UserAccount): CloudBackup {
        val user = db().collection(USERS).document(account.uid)
        val profileDoc = user.get().await()
        val steps = user.collection(DAILY_STEPS).get().await().documents.mapNotNull { it.toDailySteps() }
        val workouts = user.collection(WORKOUTS).get().await().documents.mapNotNull { it.toWorkout() }
        return CloudBackup(profile = profileDoc.toProfile(), steps = steps, workouts = workouts)
    }

    override suspend fun publishSteps(account: UserAccount, date: LocalDate, steps: Int) {
        db().collection(LEADERBOARD).document(date.toString())
            .collection(ENTRIES).document(account.uid)
            .set(mapOf("displayName" to account.displayName, "steps" to steps))
            .await()
    }

    override suspend fun leaderboard(date: LocalDate, currentUid: String): List<LeaderboardEntry> =
        db().collection(LEADERBOARD).document(date.toString()).collection(ENTRIES)
            .orderBy("steps", Query.Direction.DESCENDING)
            .limit(LEADERBOARD_SIZE)
            .get().await()
            .documents.map { doc ->
                LeaderboardEntry(
                    displayName = doc.getString("displayName").orEmpty(),
                    steps = (doc.getLong("steps") ?: 0L).toInt(),
                    isCurrentUser = doc.id == currentUid,
                )
            }

    override suspend fun fetchGuides(): List<ExerciseGuide> =
        db().collection(GUIDES).orderBy("order").get().await().documents.mapNotNull { it.toGuide() }

    override suspend fun publishGuides(guides: List<ExerciseGuide>) {
        val db = db()
        val batch = db.batch()
        guides.forEachIndexed { index, guide ->
            batch.set(
                db.collection(GUIDES).document(guide.id),
                mapOf(
                    "order" to index,
                    "type" to guide.type.name,
                    "name" to guide.name,
                    "difficulty" to guide.difficulty.name,
                    "muscleTargets" to guide.muscleTargets,
                    "instructions" to guide.instructions,
                    "videoUrl" to guide.videoUrl,
                    "videoCredit" to guide.videoCredit,
                    "sets" to guide.sets,
                    "repsPerSet" to guide.repsPerSet,
                    "restSeconds" to guide.restSeconds,
                ),
            )
        }
        batch.commit().await()
    }

    private fun DocumentSnapshot.toProfile(): UserProfile? {
        // A profile document written before any measurements were saved has only the name.
        val height = getLong("heightCm") ?: return null
        val defaults = UserProfile()
        return UserProfile(
            heightCm = height.toInt(),
            weightKg = getDouble("weightKg")?.toFloat() ?: defaults.weightKg,
            dailyStepGoal = getLong("dailyStepGoal")?.toInt() ?: defaults.dailyStepGoal,
            dailyCalorieGoal = getLong("dailyCalorieGoal")?.toInt() ?: defaults.dailyCalorieGoal,
            dailyActiveMinutesGoal = getLong("dailyActiveMinutesGoal")?.toInt()
                ?: defaults.dailyActiveMinutesGoal,
        )
    }

    private fun DocumentSnapshot.toDailySteps(): DailySteps? {
        val date = runCatching { LocalDate.parse(id) }.getOrNull() ?: return null
        return DailySteps(
            date = date,
            steps = (getLong("steps") ?: return null).toInt(),
            activeMillis = getLong("activeMillis") ?: 0L,
            updatedAt = getLong("updatedAt") ?: 0L,
        )
    }

    private fun DocumentSnapshot.toWorkout(): WorkoutSession? {
        val type = ExerciseType.fromName(getString("exerciseType")) ?: return null
        val start = getLong("startTime") ?: return null
        return WorkoutSession(
            exerciseType = type,
            reps = (getLong("reps") ?: 0L).toInt(),
            sets = (getLong("sets") ?: 1L).toInt(),
            startTime = Instant.ofEpochMilli(start),
            endTime = Instant.ofEpochMilli(getLong("endTime") ?: start),
            source = WorkoutSource.entries.firstOrNull { it.name == getString("source") }
                ?: WorkoutSource.COUNTER,
        )
    }

    private fun DocumentSnapshot.toGuide(): ExerciseGuide? {
        val type = ExerciseType.fromName(getString("type")) ?: return null
        val difficulty = Difficulty.entries.firstOrNull { it.name == getString("difficulty") } ?: return null
        return ExerciseGuide(
            id = id,
            type = type,
            name = getString("name") ?: return null,
            difficulty = difficulty,
            muscleTargets = (get("muscleTargets") as? List<*>)?.filterIsInstance<String>().orEmpty(),
            instructions = getString("instructions").orEmpty(),
            videoUrl = getString("videoUrl") ?: return null,
            videoCredit = getString("videoCredit").orEmpty(),
            sets = (getLong("sets") ?: 3L).toInt(),
            repsPerSet = (getLong("repsPerSet") ?: 10L).toInt(),
            restSeconds = (getLong("restSeconds") ?: 30L).toInt(),
        )
    }

    private companion object {
        const val USERS = "users"
        const val DAILY_STEPS = "dailySteps"
        const val WORKOUTS = "workouts"
        const val LEADERBOARD = "leaderboard"
        const val ENTRIES = "entries"
        const val GUIDES = "guides"
        const val MAX_BATCH_WRITES = 400
        const val LEADERBOARD_SIZE = 20L
    }
}
