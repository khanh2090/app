package com.fitness.companion.presentation.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitness.companion.domain.model.ExerciseType
import com.fitness.companion.domain.model.TodaySummary
import com.fitness.companion.presentation.components.ProgressRing
import com.fitness.companion.presentation.util.AppPermissions
import com.fitness.companion.presentation.util.formatCount
import com.fitness.companion.presentation.util.formatDistance
import com.fitness.companion.presentation.util.formatQuantity
import com.fitness.companion.presentation.util.openAppSettings
import com.fitness.companion.service.StepCounterService

@Composable
fun HomeScreen(
    onStartCounter: (ExerciseType) -> Unit,
    onOpenGuides: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var canTrackSteps by remember { mutableStateOf(AppPermissions.canTrackSteps(context)) }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }

    // The permission can change in system settings while the app is in the background.
    LifecycleResumeEffect(Unit) {
        canTrackSteps = AppPermissions.canTrackSteps(context)
        onPauseOrDispose { }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        canTrackSteps = AppPermissions.canTrackSteps(context)
        permissionDenied = !canTrackSteps
        if (canTrackSteps) viewModel.setTrackingEnabled(true)
    }

    val trackingActive = state.trackingEnabled && canTrackSteps && state.hasStepCounter
    // Brings the service back after an app restart or reboot when tracking was left switched on.
    LaunchedEffect(trackingActive) {
        if (trackingActive) StepCounterService.start(context)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DailyProgressCard(state.summary)

        StepTrackingCard(
            hasStepCounter = state.hasStepCounter,
            trackingActive = trackingActive,
            permissionDenied = permissionDenied && !canTrackSteps,
            onStart = {
                if (canTrackSteps) {
                    viewModel.setTrackingEnabled(true)
                } else {
                    permissionLauncher.launch(AppPermissions.stepTracking())
                }
            },
            onStop = {
                viewModel.setTrackingEnabled(false)
                StepCounterService.stop(context)
            },
            onOpenSettings = context::openAppSettings,
        )

        QuickActionsCard(onStartCounter = onStartCounter, onOpenGuides = onOpenGuides)
    }
}

@Composable
private fun DailyProgressCard(summary: TodaySummary?) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Hôm nay", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ProgressRing(
                    progress = summary?.stepProgress ?: 0f,
                    value = formatCount(summary?.steps ?: 0),
                    label = "Bước chân",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                ProgressRing(
                    progress = summary?.calorieProgress ?: 0f,
                    value = "${(summary?.caloriesKcal ?: 0.0).toInt()} kcal",
                    label = "Calo",
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f),
                )
                ProgressRing(
                    progress = summary?.activeProgress ?: 0f,
                    value = "${summary?.activeMinutes ?: 0} phút",
                    label = "Vận động",
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f),
                )
            }
            if (summary != null) {
                Text(
                    text = "Mục tiêu ${formatCount(summary.profile.dailyStepGoal)} bước · " +
                        "đã đi ${formatDistance(summary.distanceMeters)} · " +
                        formatQuantity(summary.workoutCount, "buổi tập"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StepTrackingCard(
    hasStepCounter: Boolean,
    trackingActive: Boolean,
    permissionDenied: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Theo dõi bước chân", style = MaterialTheme.typography.titleMedium)
            when {
                !hasStepCounter -> Text(
                    "Thiết bị này không có cảm biến đếm bước nên không thể tự động theo dõi " +
                        "bước chân. Bộ đếm số lần tập và video hướng dẫn vẫn hoạt động.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                trackingActive -> {
                    Text(
                        "Đang đếm bước chân ở chế độ nền.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = onStop) { Text("Dừng theo dõi") }
                }

                else -> {
                    Text(
                        if (permissionDenied) {
                            "Cần quyền hoạt động thể chất để đếm bước chân. Nếu hộp thoại xin " +
                                "quyền không còn hiện, hãy cấp quyền trong phần cài đặt ứng dụng."
                        } else {
                            "Đếm bước chân cả ngày, kể cả khi đã đóng ứng dụng."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onStart) { Text("Bắt đầu theo dõi") }
                        if (permissionDenied) {
                            TextButton(onClick = onOpenSettings) { Text("Cài đặt ứng dụng") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionsCard(
    onStartCounter: (ExerciseType) -> Unit,
    onOpenGuides: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Bắt đầu nhanh", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExerciseType.counterTypes.forEach { type ->
                    Button(onClick = { onStartCounter(type) }, modifier = Modifier.weight(1f)) {
                        Text(type.displayName)
                    }
                }
            }
            FilledTonalButton(onClick = onOpenGuides, modifier = Modifier.fillMaxWidth()) {
                Text("Video hướng dẫn tập luyện")
            }
        }
    }
}
