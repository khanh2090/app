package com.fitness.companion.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.fitness.companion.domain.model.StepBaseline
import com.fitness.companion.domain.model.UserProfile
import com.fitness.companion.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : UserPreferencesRepository {

    // A corrupt or unreadable file falls back to defaults instead of crashing the app.
    private val preferences: Flow<Preferences> = dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    override val userProfile: Flow<UserProfile> = preferences.map { prefs ->
        val defaults = UserProfile()
        UserProfile(
            heightCm = prefs[HEIGHT_CM] ?: defaults.heightCm,
            weightKg = prefs[WEIGHT_KG] ?: defaults.weightKg,
            dailyStepGoal = prefs[STEP_GOAL] ?: defaults.dailyStepGoal,
            dailyCalorieGoal = prefs[CALORIE_GOAL] ?: defaults.dailyCalorieGoal,
            dailyActiveMinutesGoal = prefs[ACTIVE_MINUTES_GOAL] ?: defaults.dailyActiveMinutesGoal,
        )
    }.distinctUntilChanged()

    override val stepTrackingEnabled: Flow<Boolean> =
        preferences.map { it[STEP_TRACKING_ENABLED] ?: false }.distinctUntilChanged()

    override suspend fun updateProfile(profile: UserProfile) {
        dataStore.edit { prefs ->
            prefs[HEIGHT_CM] = profile.heightCm
            prefs[WEIGHT_KG] = profile.weightKg
            prefs[STEP_GOAL] = profile.dailyStepGoal
            prefs[CALORIE_GOAL] = profile.dailyCalorieGoal
            prefs[ACTIVE_MINUTES_GOAL] = profile.dailyActiveMinutesGoal
        }
    }

    override suspend fun setStepTrackingEnabled(enabled: Boolean) {
        dataStore.edit { it[STEP_TRACKING_ENABLED] = enabled }
    }

    override suspend fun getStepBaseline(): StepBaseline? {
        val prefs = preferences.first()
        val total = prefs[BASELINE_TOTAL] ?: return null
        val day = prefs[BASELINE_DAY] ?: return null
        return StepBaseline(sensorTotal = total, epochDay = day)
    }

    override suspend fun setStepBaseline(baseline: StepBaseline?) {
        dataStore.edit { prefs ->
            if (baseline == null) {
                prefs.remove(BASELINE_TOTAL)
                prefs.remove(BASELINE_DAY)
            } else {
                prefs[BASELINE_TOTAL] = baseline.sensorTotal
                prefs[BASELINE_DAY] = baseline.epochDay
            }
        }
    }

    private companion object {
        val HEIGHT_CM = intPreferencesKey("height_cm")
        val WEIGHT_KG = floatPreferencesKey("weight_kg")
        val STEP_GOAL = intPreferencesKey("daily_step_goal")
        val CALORIE_GOAL = intPreferencesKey("daily_calorie_goal")
        val ACTIVE_MINUTES_GOAL = intPreferencesKey("daily_active_minutes_goal")
        val STEP_TRACKING_ENABLED = booleanPreferencesKey("step_tracking_enabled")
        val BASELINE_TOTAL = longPreferencesKey("step_baseline_total")
        val BASELINE_DAY = longPreferencesKey("step_baseline_day")
    }
}
