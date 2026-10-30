package com.fitness.companion.di

import android.content.Context
import android.hardware.SensorManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.fitness.companion.data.cloud.FirebaseAccountRepository
import com.fitness.companion.data.cloud.FirestoreCloudStore
import com.fitness.companion.data.feedback.ToneFeedbackPlayer
import com.fitness.companion.domain.repository.AccountRepository
import com.fitness.companion.domain.repository.CloudStore
import com.fitness.companion.data.health.HealthConnectManager
import com.fitness.companion.data.local.AppDatabase
import com.fitness.companion.data.local.DailyStepsDao
import com.fitness.companion.data.local.WorkoutSessionDao
import com.fitness.companion.data.repository.ExerciseGuideRepositoryImpl
import com.fitness.companion.data.repository.StepRepositoryImpl
import com.fitness.companion.data.repository.UserPreferencesRepositoryImpl
import com.fitness.companion.data.repository.WorkoutRepositoryImpl
import com.fitness.companion.data.sensor.AccelerometerSensorSource
import com.fitness.companion.data.sensor.AndroidDeviceCapabilities
import com.fitness.companion.data.sensor.ProximitySensorSource
import com.fitness.companion.domain.repository.ExerciseGuideRepository
import com.fitness.companion.domain.repository.HealthSyncRepository
import com.fitness.companion.domain.repository.StepRepository
import com.fitness.companion.domain.repository.UserPreferencesRepository
import com.fitness.companion.domain.repository.WorkoutRepository
import com.fitness.companion.domain.sensor.AccelerometerSource
import com.fitness.companion.domain.sensor.DeviceCapabilities
import com.fitness.companion.domain.sensor.FeedbackPlayer
import com.fitness.companion.domain.sensor.ProximitySource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Framework objects that need a factory call. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "fitness_companion.db").build()

    @Provides
    fun provideDailyStepsDao(database: AppDatabase): DailyStepsDao = database.dailyStepsDao()

    @Provides
    fun provideWorkoutSessionDao(database: AppDatabase): WorkoutSessionDao =
        database.workoutSessionDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile("user_preferences")
        }

    @Provides
    @Singleton
    fun provideSensorManager(@ApplicationContext context: Context): SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
}

/** Maps each domain interface to its data-layer implementation. */
@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {

    @Binds
    abstract fun bindStepRepository(impl: StepRepositoryImpl): StepRepository

    @Binds
    abstract fun bindWorkoutRepository(impl: WorkoutRepositoryImpl): WorkoutRepository

    @Binds
    abstract fun bindUserPreferencesRepository(impl: UserPreferencesRepositoryImpl): UserPreferencesRepository

    @Binds
    abstract fun bindExerciseGuideRepository(impl: ExerciseGuideRepositoryImpl): ExerciseGuideRepository

    @Binds
    abstract fun bindHealthSyncRepository(impl: HealthConnectManager): HealthSyncRepository

    @Binds
    abstract fun bindProximitySource(impl: ProximitySensorSource): ProximitySource

    @Binds
    abstract fun bindAccelerometerSource(impl: AccelerometerSensorSource): AccelerometerSource

    @Binds
    abstract fun bindDeviceCapabilities(impl: AndroidDeviceCapabilities): DeviceCapabilities

    @Binds
    abstract fun bindFeedbackPlayer(impl: ToneFeedbackPlayer): FeedbackPlayer

    @Binds
    abstract fun bindAccountRepository(impl: FirebaseAccountRepository): AccountRepository

    @Binds
    abstract fun bindCloudStore(impl: FirestoreCloudStore): CloudStore
}
