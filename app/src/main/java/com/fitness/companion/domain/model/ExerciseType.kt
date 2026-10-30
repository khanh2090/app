package com.fitness.companion.domain.model

/**
 * Exercises the app knows about.
 *
 * @property met metabolic equivalent used for calorie estimates (Compendium of Physical
 * Activities, calisthenics entries).
 */
enum class ExerciseType(val displayName: String, val met: Double) {
    PUSH_UP("Chống đẩy", 3.8),
    SIT_UP("Gập bụng", 2.8),
    SQUAT("Squat", 5.0),
    LUNGE("Chùng chân", 3.8),
    JUMPING_JACK("Bật nhảy dang tay", 8.0),
    BURPEE("Burpee", 8.0),
    PULL_UP("Kéo xà", 8.0),
    SPRAWL("Sprawl", 8.0),
    KETTLEBELL_SWING("Vung tạ ấm", 9.8),
    SINGLE_LEG_SQUAT("Squat một chân", 5.0);

    companion object {
        /** Exercises the sensor-based rep counter supports. */
        val counterTypes: List<ExerciseType> = listOf(PUSH_UP, SIT_UP)

        fun fromName(name: String?): ExerciseType? = entries.firstOrNull { it.name == name }
    }
}
