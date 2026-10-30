package com.fitness.companion.presentation.counter

import android.Manifest
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitness.companion.domain.model.ExerciseType
import com.fitness.companion.presentation.util.AppPermissions
import com.fitness.companion.presentation.util.formatDuration
import com.fitness.companion.presentation.util.openAppSettings

/**
 * @param requestedExercise exercise chosen with a quick-start button on the home screen, or null
 * @param onExerciseRequestHandled called once that choice has been applied
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CounterScreen(
    requestedExercise: ExerciseType?,
    onExerciseRequestHandled: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CounterViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val view = LocalView.current
    val idle = state.status == CounterStatus.IDLE
    val running = state.status == CounterStatus.RUNNING

    // A session that is already running keeps its exercise; the view model ignores the request.
    LaunchedEffect(requestedExercise) {
        if (requestedExercise != null) {
            viewModel.selectExercise(requestedExercise)
            onExerciseRequestHandled()
        }
    }

    // Pause counting when the app is minimised, but not when the screen merely rotates.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (activity?.isChangingConfigurations != true) viewModel.onAppBackgrounded()
    }

    // The phone lies on the floor while counting, so the display must not time out.
    DisposableEffect(running) {
        view.keepScreenOn = running
        onDispose { view.keepScreenOn = false }
    }

    var cameraGranted by remember {
        mutableStateOf(AppPermissions.isGranted(context, Manifest.permission.CAMERA))
    }
    var cameraDenied by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        cameraGranted = AppPermissions.isGranted(context, Manifest.permission.CAMERA)
        onPauseOrDispose { }
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        cameraGranted = granted
        cameraDenied = !granted
    }
    val needsCameraPermission = state.mode == CounterMode.CAMERA && !cameraGranted

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ExerciseType.counterTypes.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = state.exercise == type,
                    onClick = { viewModel.selectExercise(type) },
                    shape = SegmentedButtonDefaults.itemShape(index, ExerciseType.counterTypes.size),
                    enabled = idle,
                ) {
                    Text(type.displayName)
                }
            }
        }

        // Wraps onto a second line when the mode names do not fit side by side.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.availableModes.forEach { mode ->
                FilterChip(
                    selected = state.mode == mode,
                    onClick = { viewModel.selectMode(mode) },
                    label = { Text(mode.label) },
                    enabled = idle,
                )
            }
        }

        state.fallbackNotice?.let { notice ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(notice, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
            }
        }

        Text(
            text = instructions(state.mode, state.exercise),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.mode == CounterMode.CAMERA) {
            if (cameraGranted) {
                CameraPoseCounter(
                    exercise = state.exercise,
                    onAngle = viewModel::onPoseAngle,
                    // Kept small so the rep count stays on screen below the preview.
                    modifier = Modifier
                        .widthIn(max = 210.dp)
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(16.dp)),
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        if (cameraDenied) {
                            "Đếm bằng camera cần quyền dùng camera. Nếu hộp thoại xin quyền không " +
                                "còn hiện, hãy cấp quyền trong phần cài đặt ứng dụng."
                        } else {
                            "Đếm bằng camera cần quyền dùng camera. Hình ảnh được phân tích ngay " +
                                "trên thiết bị và không được lưu lại."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { cameraLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("Cho phép camera")
                        }
                        if (cameraDenied) {
                            TextButton(onClick = context::openAppSettings) { Text("Cài đặt ứng dụng") }
                        }
                    }
                }
            }
        }

        RepCount(
            reps = state.reps,
            tappable = running && state.mode == CounterMode.MANUAL,
            onTap = viewModel::onManualRep,
        )

        Text(
            text = formatDuration(state.elapsedMs),
            style = MaterialTheme.typography.headlineSmall,
        )

        SignalIndicator(state.signal)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            when (state.status) {
                CounterStatus.IDLE -> Button(
                    onClick = viewModel::start,
                    enabled = !needsCameraPermission,
                ) { Text("Bắt đầu") }

                CounterStatus.RUNNING -> {
                    Button(onClick = viewModel::pause) { Text("Tạm dừng") }
                    OutlinedButton(onClick = viewModel::stop) { Text("Dừng") }
                }

                CounterStatus.PAUSED -> {
                    Button(onClick = viewModel::resume) { Text("Tiếp tục") }
                    OutlinedButton(onClick = viewModel::stop) { Text("Dừng") }
                }
            }
        }

        state.lastResult?.let { result ->
            Text(
                text = result,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
private fun RepCount(reps: Int, tappable: Boolean, onTap: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .then(if (tappable) Modifier.clickable(onClickLabel = "Thêm một lần", onClick = onTap) else Modifier)
            .padding(horizontal = 48.dp, vertical = 8.dp)
            .semantics {
                contentDescription = "$reps lần"
                liveRegion = LiveRegionMode.Polite
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = reps.toString(),
            fontSize = 112.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SignalIndicator(signal: SensorSignal) {
    val color = when (signal.level) {
        SignalLevel.OFF -> MaterialTheme.colorScheme.outline
        SignalLevel.WAITING -> MaterialTheme.colorScheme.tertiary
        SignalLevel.ACTIVE -> MaterialTheme.colorScheme.primary
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .background(color, CircleShape),
        )
        Text(signal.label, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun instructions(mode: CounterMode, exercise: ExerciseType): String = when (mode) {
    CounterMode.PROXIMITY -> if (exercise == ExerciseType.SIT_UP) {
        "Đặt điện thoại gần đầu gối, màn hình hướng lên, sao cho ngực che phần trên màn hình " +
            "khi bạn gập người lên hết mức."
    } else {
        "Đặt điện thoại trên sàn ngay dưới ngực, màn hình hướng lên. Hạ người xuống sát máy " +
            "rồi đẩy lên được tính là một lần."
    }
    CounterMode.ACCELEROMETER ->
        "Đeo điện thoại ở bắp tay hoặc giữ sát ngực để máy chuyển động cùng cơ thể."
    CounterMode.CAMERA ->
        "Dựng điện thoại sao cho camera thấy toàn bộ cơ thể bạn từ bên cạnh."
    CounterMode.MANUAL -> "Chạm vào con số lớn sau mỗi lần tập."
}
