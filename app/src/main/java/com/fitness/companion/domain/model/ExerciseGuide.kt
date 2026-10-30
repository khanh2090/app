package com.fitness.companion.domain.model

enum class Difficulty(val label: String) {
    BEGINNER("Người mới"),
    INTERMEDIATE("Trung bình"),
    ADVANCED("Nâng cao"),
}

/** A video-guided exercise with its recommended set plan. */
data class ExerciseGuide(
    val id: String,
    val type: ExerciseType,
    val name: String,
    val difficulty: Difficulty,
    val muscleTargets: List<String>,
    val instructions: String,
    val videoUrl: String,
    /** Author and licence of the video, shown next to the player. */
    val videoCredit: String,
    val sets: Int,
    val repsPerSet: Int,
    val restSeconds: Int,
)
