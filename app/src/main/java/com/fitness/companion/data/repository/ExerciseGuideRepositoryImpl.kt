package com.fitness.companion.data.repository

import com.fitness.companion.domain.model.Difficulty
import com.fitness.companion.domain.model.ExerciseGuide
import com.fitness.companion.domain.model.ExerciseType
import com.fitness.companion.domain.repository.CloudStore
import com.fitness.companion.domain.repository.ExerciseGuideRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Built-in exercise catalogue. The demonstration clips are freely licensed files from Wikimedia
 * Commons, bundled with the app; each entry carries the attribution its licence requires.
 */
@Singleton
class ExerciseGuideRepositoryImpl @Inject constructor(
    private val cloudStore: CloudStore,
) : ExerciseGuideRepository {

    private val catalogue = MutableStateFlow(builtInGuides)

    override val guides: StateFlow<List<ExerciseGuide>> = catalogue.asStateFlow()

    override fun getById(id: String): ExerciseGuide? = catalogue.value.firstOrNull { it.id == id }

    override suspend fun refresh() {
        try {
            val remote = cloudStore.fetchGuides()
            if (remote.isNotEmpty()) catalogue.value = remote
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // No backend, no network or no access: the catalogue already shown stays.
        }
    }

    override fun builtIn(): List<ExerciseGuide> = builtInGuides

    private companion object {
        /** Clips are bundled in `res/raw`, so they play without a network connection. */
        const val RAW = "android.resource://com.fitness.companion/raw"

        val builtInGuides = listOf(
            ExerciseGuide(
                id = "push-up",
                type = ExerciseType.PUSH_UP,
                name = "Chống đẩy",
                difficulty = Difficulty.INTERMEDIATE,
                muscleTargets = listOf("Ngực", "Tay sau", "Vai", "Cơ lõi"),
                instructions = "Đặt hai tay rộng hơn vai một chút, giữ thân người thành một đường " +
                    "thẳng. Hạ ngực xuống gần chạm sàn rồi đẩy người lên.",
                videoUrl = "$RAW/guide_push_up",
                videoCredit = "Video: Taco fleur, CC BY-SA 4.0, qua Wikimedia Commons",
                sets = 3,
                repsPerSet = 12,
                restSeconds = 45,
            ),
            ExerciseGuide(
                id = "sit-up",
                type = ExerciseType.SIT_UP,
                name = "Gập bụng",
                difficulty = Difficulty.BEGINNER,
                muscleTargets = listOf("Cơ bụng", "Cơ gập hông"),
                instructions = "Nằm ngửa, co gối, hai bàn chân đặt trên sàn. Cuộn thân trên về phía " +
                    "đầu gối rồi hạ xuống có kiểm soát.",
                videoUrl = "$RAW/guide_sit_up",
                videoCredit = "Video: Wxhsyhzz, CC BY-SA 3.0, qua Wikimedia Commons",
                sets = 3,
                repsPerSet = 15,
                restSeconds = 30,
            ),
            ExerciseGuide(
                id = "squat",
                type = ExerciseType.SQUAT,
                name = "Squat không tạ",
                difficulty = Difficulty.BEGINNER,
                muscleTargets = listOf("Đùi trước", "Mông", "Đùi sau"),
                instructions = "Hai chân rộng bằng vai. Đẩy hông ra sau và gập gối đến khi đùi song " +
                    "song với sàn, luôn giữ ngực thẳng.",
                videoUrl = "$RAW/guide_squat",
                videoCredit = "Video: FitnessScape, CC BY 3.0, qua Wikimedia Commons",
                sets = 3,
                repsPerSet = 15,
                restSeconds = 45,
            ),
            ExerciseGuide(
                id = "lunge",
                type = ExerciseType.LUNGE,
                name = "Chùng chân bước tới",
                difficulty = Difficulty.INTERMEDIATE,
                muscleTargets = listOf("Đùi trước", "Mông", "Bắp chân"),
                instructions = "Bước một chân lên trước và hạ hông đến khi cả hai gối gập khoảng " +
                    "90 độ. Đạp gót chân trước để trở về rồi đổi chân.",
                videoUrl = "$RAW/guide_lunge",
                videoCredit = "Video: Lục quân Hoa Kỳ (Army Combat Fitness Test), phạm vi công cộng, " +
                    "qua Wikimedia Commons",
                sets = 3,
                repsPerSet = 10,
                restSeconds = 45,
            ),
            ExerciseGuide(
                id = "jumping-jack",
                type = ExerciseType.JUMPING_JACK,
                name = "Bật nhảy dang tay",
                difficulty = Difficulty.BEGINNER,
                muscleTargets = listOf("Toàn thân", "Tim mạch"),
                instructions = "Bật nhảy dang hai chân đồng thời đưa hai tay lên cao qua đầu, rồi " +
                    "bật về tư thế ban đầu. Giữ nhịp đều.",
                videoUrl = "$RAW/guide_jumping_jack",
                videoCredit = "Video: Earthperp, CC0, qua Wikimedia Commons",
                sets = 3,
                repsPerSet = 30,
                restSeconds = 30,
            ),
            ExerciseGuide(
                id = "burpee",
                type = ExerciseType.BURPEE,
                name = "Burpee",
                difficulty = Difficulty.ADVANCED,
                muscleTargets = listOf("Toàn thân", "Ngực", "Chân", "Tim mạch"),
                instructions = "Ngồi xổm, bật hai chân ra sau về tư thế plank, chống đẩy một lần, " +
                    "thu chân về rồi kết thúc bằng một cú bật nhảy.",
                videoUrl = "$RAW/guide_burpee",
                videoCredit = "Video: Taco fleur, CC BY-SA 4.0, qua Wikimedia Commons",
                sets = 3,
                repsPerSet = 8,
                restSeconds = 60,
            ),
            ExerciseGuide(
                id = "pull-up",
                type = ExerciseType.PULL_UP,
                name = "Kéo xà",
                difficulty = Difficulty.ADVANCED,
                muscleTargets = listOf("Lưng xô", "Tay trước", "Vai"),
                instructions = "Nắm xà rộng hơn vai, treo người thẳng tay. Kéo người lên đến khi " +
                    "cằm qua xà rồi hạ xuống có kiểm soát, không đung đưa.",
                videoUrl = "$RAW/guide_pull_up",
                videoCredit = "Video: Thủy quân lục chiến Hoa Kỳ (Lance Cpl. Andrew Knight), " +
                    "phạm vi công cộng, qua Wikimedia Commons",
                sets = 3,
                repsPerSet = 6,
                restSeconds = 60,
            ),
            ExerciseGuide(
                id = "sprawl",
                type = ExerciseType.SPRAWL,
                name = "Sprawl",
                difficulty = Difficulty.INTERMEDIATE,
                muscleTargets = listOf("Toàn thân", "Cơ lõi", "Tim mạch"),
                instructions = "Từ tư thế đứng, chống hai tay xuống sàn và bật hai chân ra sau, hạ " +
                    "hông sát sàn. Thu chân về rồi đứng thẳng dậy.",
                videoUrl = "$RAW/guide_sprawl",
                videoCredit = "Video: Taco fleur, CC BY-SA 4.0, qua Wikimedia Commons",
                sets = 3,
                repsPerSet = 10,
                restSeconds = 45,
            ),
            ExerciseGuide(
                id = "kettlebell-swing",
                type = ExerciseType.KETTLEBELL_SWING,
                name = "Vung tạ ấm",
                difficulty = Difficulty.INTERMEDIATE,
                muscleTargets = listOf("Mông", "Đùi sau", "Lưng dưới", "Vai"),
                instructions = "Hai chân rộng hơn vai, giữ lưng thẳng. Đưa tạ ra sau giữa hai chân " +
                    "rồi bật hông về trước để vung tạ lên ngang ngực.",
                videoUrl = "$RAW/guide_kettlebell_swing",
                videoCredit = "Video: Taco fleur, CC BY-SA 4.0, qua Wikimedia Commons",
                sets = 3,
                repsPerSet = 15,
                restSeconds = 45,
            ),
            ExerciseGuide(
                id = "single-leg-squat",
                type = ExerciseType.SINGLE_LEG_SQUAT,
                name = "Squat một chân",
                difficulty = Difficulty.ADVANCED,
                muscleTargets = listOf("Đùi trước", "Mông", "Thăng bằng"),
                instructions = "Đứng trên một chân, chân kia duỗi ra trước. Hạ hông xuống chậm, giữ " +
                    "gối thẳng hướng mũi chân, rồi đứng lên. Đổi chân sau mỗi hiệp.",
                videoUrl = "$RAW/guide_single_leg_squat",
                videoCredit = "Video: RickyBennison, CC BY-SA 4.0, qua Wikimedia Commons",
                sets = 3,
                repsPerSet = 6,
                restSeconds = 60,
            ),
        )
    }
}
