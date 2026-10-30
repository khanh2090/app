package com.fitness.companion.presentation.profile

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.health.connect.client.PermissionController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitness.companion.domain.model.HealthAvailability
import com.fitness.companion.presentation.account.AccountCard
import com.fitness.companion.presentation.util.formatCount

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Health Connect can be installed or its permissions changed while the app is in the background.
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshHealth()
        onPauseOrDispose { }
    }

    val healthPermissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
        viewModel::onHealthPermissionsResult,
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.loaded) {
            ProfileFormCard(
                form = state.form,
                savedMessage = state.savedMessage,
                onChange = viewModel::updateForm,
                onSave = viewModel::saveProfile,
            )
        }
        AccountCard()
        HealthConnectCard(
            state = state,
            onGrantAccess = { healthPermissionLauncher.launch(viewModel.healthPermissions) },
            onSync = viewModel::sync,
            onInstall = { openHealthConnectInStore(context) },
        )
    }
}

@Composable
private fun ProfileFormCard(
    form: ProfileForm,
    savedMessage: String?,
    onChange: ((ProfileForm) -> ProfileForm) -> Unit,
    onSave: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Cơ thể và mục tiêu", style = MaterialTheme.typography.titleMedium)
            Text(
                "Chiều cao và cân nặng dùng để ước tính quãng đường và lượng calo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NumberField("Chiều cao (cm)", form.heightCm, form.heightError) { value ->
                onChange { it.copy(heightCm = value) }
            }
            NumberField("Cân nặng (kg)", form.weightKg, form.weightError, decimal = true) { value ->
                onChange { it.copy(weightKg = value) }
            }
            NumberField("Mục tiêu bước mỗi ngày", form.stepGoal, form.stepGoalError) { value ->
                onChange { it.copy(stepGoal = value) }
            }
            NumberField("Mục tiêu calo mỗi ngày (kcal)", form.calorieGoal, form.calorieGoalError) { value ->
                onChange { it.copy(calorieGoal = value) }
            }
            NumberField("Mục tiêu vận động mỗi ngày (phút)", form.activeMinutesGoal, form.activeMinutesError) { value ->
                onChange { it.copy(activeMinutesGoal = value) }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = onSave, enabled = form.isValid) { Text("Lưu") }
                savedMessage?.let { message ->
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    error: String?,
    decimal: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { message -> { Text(message) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun HealthConnectCard(
    state: ProfileUiState,
    onGrantAccess: () -> Unit,
    onSync: () -> Unit,
    onInstall: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Health Connect", style = MaterialTheme.typography.titleMedium)
            when (state.healthAvailability) {
                HealthAvailability.UNSUPPORTED -> Text(
                    "Thiết bị này không hỗ trợ Health Connect. Dữ liệu của bạn vẫn được lưu " +
                        "trong ứng dụng.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                HealthAvailability.UPDATE_REQUIRED -> {
                    Text(
                        "Hãy cài đặt hoặc cập nhật Health Connect để chia sẻ bước chân và buổi " +
                            "tập với các ứng dụng sức khỏe khác.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = onInstall) { Text("Tải Health Connect") }
                }

                HealthAvailability.AVAILABLE -> if (state.healthPermissionsGranted) {
                    Text(
                        "Bước chân và buổi tập đang được chia sẻ với Health Connect.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    state.healthConnectSteps?.let { steps ->
                        Text(
                            "Health Connect ghi nhận ${formatCount(steps.toInt())} bước hôm nay.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(onClick = onSync, enabled = !state.syncing) {
                        Text(if (state.syncing) "Đang đồng bộ…" else "Đồng bộ ngay")
                    }
                } else {
                    Text(
                        "Cho phép ứng dụng ghi bước chân và buổi tập vào Health Connect, đồng " +
                            "thời đọc tổng số bước hôm nay.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = onGrantAccess) { Text("Cấp quyền") }
                }
            }
            state.syncMessage?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

/** Opens the Health Connect listing in the Play Store, or the web listing without the store. */
private fun openHealthConnectInStore(context: Context) {
    val packageName = "com.google.android.apps.healthdata"
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri())
                .setPackage("com.android.vending"),
        )
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    "https://play.google.com/store/apps/details?id=$packageName".toUri(),
                ),
            )
        } catch (e: ActivityNotFoundException) {
            // No store and no browser: nothing can be opened on this device.
        }
    }
}
