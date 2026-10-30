package com.fitness.companion.presentation.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.fitness.companion.domain.model.Difficulty
import com.fitness.companion.domain.model.ExerciseGuide
import com.fitness.companion.domain.repository.ExerciseGuideRepository
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GuidesViewModel @Inject constructor(
    guideRepository: ExerciseGuideRepository,
) : ViewModel() {
    val guides: StateFlow<List<ExerciseGuide>> = guideRepository.guides

    init {
        // Shows the built-in catalogue at once and swaps in the backend's when it arrives.
        viewModelScope.launch { guideRepository.refresh() }
    }
}

@Composable
fun GuidesScreen(
    onStartWorkout: (ExerciseGuide) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GuidesViewModel = hiltViewModel(),
) {
    // Only one preview plays at a time, so the list never holds more than one video player.
    var previewedId by rememberSaveable { mutableStateOf<String?>(null) }
    val guides by viewModel.guides.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(guides, key = { it.id }) { guide ->
            GuideCard(
                guide = guide,
                showPreview = previewedId == guide.id,
                onTogglePreview = { previewedId = if (previewedId == guide.id) null else guide.id },
                onStartWorkout = { onStartWorkout(guide) },
            )
        }
    }
}

/** Difficulty label; advanced exercises stand out in the tertiary colour. */
@Composable
fun DifficultyBadge(difficulty: Difficulty, modifier: Modifier = Modifier) {
    val (container, content) = when (difficulty) {
        Difficulty.BEGINNER ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        Difficulty.INTERMEDIATE ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        Difficulty.ADVANCED ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    }
    Text(
        text = difficulty.label,
        style = MaterialTheme.typography.labelMedium,
        color = content,
        modifier = modifier
            .background(container, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
private fun GuideCard(
    guide: ExerciseGuide,
    showPreview: Boolean,
    onTogglePreview: () -> Unit,
    onStartWorkout: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    guide.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                DifficultyBadge(guide.difficulty)
            }
            Text(
                "Nhóm cơ: ${guide.muscleTargets.joinToString(", ")}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "${guide.sets} hiệp × ${guide.repsPerSet} lần · nghỉ ${guide.restSeconds} giây",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (showPreview) {
                VideoPreview(
                    videoUrl = guide.videoUrl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp)),
                )
                Text(
                    guide.videoCredit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onTogglePreview) {
                    Text(if (showPreview) "Ẩn xem trước" else "Xem trước")
                }
                Button(onClick = onStartWorkout) { Text("Bắt đầu tập") }
            }
        }
    }
}
