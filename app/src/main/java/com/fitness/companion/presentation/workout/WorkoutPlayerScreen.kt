package com.fitness.companion.presentation.workout

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitness.companion.domain.model.ExerciseGuide
import com.fitness.companion.presentation.util.formatDuration

@Composable
fun WorkoutPlayerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkoutPlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val guide = state.guide
    val player = viewModel.player

    // Pause the video and the rest timer when the app is minimised, but not on rotation.
    LifecycleStartEffect(viewModel) {
        viewModel.onForegrounded()
        onStopOrDispose {
            if (activity?.isChangingConfigurations != true) viewModel.onBackgrounded()
        }
    }

    if (guide == null || player == null) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Không tìm thấy bài tập này.", style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onBack) { Text("Quay lại danh sách") }
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp)),
        ) {
            GuidePlayerView(player = player, showControls = true, modifier = Modifier.fillMaxSize())
        }

        if (state.playbackError) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Không tải được video. Hãy kiểm tra kết nối mạng.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = viewModel::retryPlayback) { Text("Thử lại") }
            }
        }

        SetTrackerCard(
            guide = guide,
            state = state,
            onFinishSet = viewModel::finishSet,
            onSkipRest = viewModel::skipRest,
            onRestart = viewModel::restart,
        )

        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(guide.name, style = MaterialTheme.typography.titleLarge)
                DifficultyBadge(guide.difficulty)
            }
            Text(
                "Nhóm cơ: ${guide.muscleTargets.joinToString(", ")}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(guide.instructions, style = MaterialTheme.typography.bodyMedium)
            Text(
                guide.videoCredit,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SetTrackerCard(
    guide: ExerciseGuide,
    state: WorkoutPlayerUiState,
    onFinishSet: () -> Unit,
    onSkipRest: () -> Unit,
    onRestart: () -> Unit,
) {
    val completedSets = when (state.phase) {
        WorkoutPhase.EXERCISING -> state.currentSet - 1
        WorkoutPhase.RESTING -> state.currentSet
        WorkoutPhase.COMPLETED -> guide.sets
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LinearProgressIndicator(
                progress = { completedSets.toFloat() / guide.sets },
                modifier = Modifier.fillMaxWidth(),
            )
            when (state.phase) {
                WorkoutPhase.EXERCISING -> {
                    Text(
                        "Hiệp ${state.currentSet}/${guide.sets}",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text("${guide.repsPerSet} lần", style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = onFinishSet) { Text("Xong hiệp") }
                }

                WorkoutPhase.RESTING -> {
                    Text("Nghỉ", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = formatDuration(state.restRemainingSeconds * 1000L),
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    Text(
                        "Tiếp theo: hiệp ${state.currentSet + 1}/${guide.sets}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = onSkipRest) { Text("Bỏ qua nghỉ") }
                }

                WorkoutPhase.COMPLETED -> {
                    Text("Hoàn thành buổi tập", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Đã lưu ${guide.sets} hiệp × ${guide.repsPerSet} lần vào lịch sử.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = onRestart) { Text("Tập lại") }
                }
            }
        }
    }
}
