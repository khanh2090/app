package com.fitness.companion.presentation.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitness.companion.domain.repository.AccountRepository
import com.fitness.companion.domain.repository.CloudStore
import com.fitness.companion.domain.repository.LeaderboardEntry
import com.fitness.companion.domain.repository.UserAccount
import com.fitness.companion.domain.usecase.CloudSyncUseCase
import com.fitness.companion.presentation.util.formatCount
import com.fitness.companion.presentation.util.formatQuantity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class AccountUiState(
    val configured: Boolean,
    val account: UserAccount? = null,
    val email: String = "",
    val password: String = "",
    val displayName: String = "",
    val busy: Boolean = false,
    val message: String? = null,
    /** Today's step ranking, or null until it has been loaded. */
    val leaderboard: List<LeaderboardEntry>? = null,
) {
    val canSignIn: Boolean get() = !busy && '@' in email && password.length >= MIN_PASSWORD_LENGTH
    val canSignUp: Boolean get() = canSignIn && displayName.isNotBlank()

    companion object {
        const val MIN_PASSWORD_LENGTH = 6
    }
}

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val accounts: AccountRepository,
    private val cloudStore: CloudStore,
    private val cloudSync: CloudSyncUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountUiState(configured = accounts.isConfigured))
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            accounts.account.collect { account ->
                // The password is not kept in memory once it has done its job.
                _uiState.update { it.copy(account = account, password = "", leaderboard = null) }
            }
        }
    }

    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value.trim(), message = null) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, message = null) }
    fun onDisplayNameChange(value: String) = _uiState.update { it.copy(displayName = value, message = null) }

    fun signIn() = runBackendCall { state ->
        accounts.signIn(state.email, state.password)
        "Đã đăng nhập"
    }

    fun signUp() = runBackendCall { state ->
        accounts.signUp(state.email, state.password, state.displayName.trim())
        "Đã tạo tài khoản"
    }

    fun signOut() {
        accounts.signOut()
        _uiState.update { it.copy(message = "Đã đăng xuất") }
    }

    fun sync() = runBackendCall { state ->
        val account = state.account ?: return@runBackendCall "Hãy đăng nhập trước"
        val result = cloudSync(account)
        "Đã đồng bộ ${formatQuantity(result.uploadedDays, "ngày bước chân")} và " +
            "${formatQuantity(result.uploadedWorkouts, "buổi tập")}; khôi phục " +
            "${formatQuantity(result.restoredWorkouts, "buổi tập")} từ đám mây."
    }

    fun loadLeaderboard() = runBackendCall { state ->
        val account = state.account ?: return@runBackendCall "Hãy đăng nhập trước"
        val entries = cloudStore.leaderboard(LocalDate.now(), account.uid)
        _uiState.update { it.copy(leaderboard = entries) }
        null
    }

    /** Runs one backend request, showing its outcome or its failure as a message. */
    private fun runBackendCall(call: suspend (AccountUiState) -> String?) {
        val state = _uiState.value
        if (state.busy) return
        _uiState.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val message = try {
                call(state)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "Không thực hiện được: ${e.localizedMessage ?: "lỗi không xác định"}"
            }
            _uiState.update { it.copy(busy = false, message = message) }
        }
    }
}

/** Account, cloud backup and leaderboard card, shown on the profile screen. */
@Composable
fun AccountCard(
    modifier: Modifier = Modifier,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val account = state.account

    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tài khoản và đám mây", style = MaterialTheme.typography.titleMedium)
            when {
                !state.configured -> Text(
                    "Bản cài này chưa được nối với máy chủ, nên chưa đăng nhập hay sao lưu được. " +
                        "Dữ liệu của bạn vẫn được lưu trên máy.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                account == null -> SignInForm(state, viewModel)

                else -> {
                    Text(
                        "Đã đăng nhập: ${account.displayName} (${account.email})",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = viewModel::sync, enabled = !state.busy) {
                            Text(if (state.busy) "Đang xử lý…" else "Đồng bộ đám mây")
                        }
                        OutlinedButton(onClick = viewModel::loadLeaderboard, enabled = !state.busy) {
                            Text("Bảng xếp hạng")
                        }
                    }
                    TextButton(onClick = viewModel::signOut, enabled = !state.busy) { Text("Đăng xuất") }
                    state.leaderboard?.let { Leaderboard(it) }
                }
            }
            state.message?.let { message ->
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

@Composable
private fun SignInForm(state: AccountUiState, viewModel: AccountViewModel) {
    Text(
        "Đăng nhập để sao lưu bước chân và buổi tập lên đám mây, khôi phục trên máy khác và " +
            "xem bảng xếp hạng.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = state.email,
        onValueChange = viewModel::onEmailChange,
        label = { Text("Email") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.password,
        onValueChange = viewModel::onPasswordChange,
        label = { Text("Mật khẩu (ít nhất ${AccountUiState.MIN_PASSWORD_LENGTH} ký tự)") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.displayName,
        onValueChange = viewModel::onDisplayNameChange,
        label = { Text("Tên hiển thị (chỉ cần khi đăng ký)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = viewModel::signIn, enabled = state.canSignIn) { Text("Đăng nhập") }
        OutlinedButton(onClick = viewModel::signUp, enabled = state.canSignUp) { Text("Đăng ký") }
    }
}

@Composable
private fun Leaderboard(entries: List<LeaderboardEntry>) {
    HorizontalDivider()
    Text("Bảng xếp hạng bước chân hôm nay", style = MaterialTheme.typography.titleSmall)
    if (entries.isEmpty()) {
        Text(
            "Chưa ai có số bước hôm nay. Hãy bấm Đồng bộ đám mây để gửi số bước của bạn.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    entries.forEachIndexed { index, entry ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "${index + 1}. ${entry.displayName}" + if (entry.isCurrentUser) " (bạn)" else "",
                fontWeight = if (entry.isCurrentUser) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
            )
            Text("${formatCount(entry.steps)} bước")
        }
    }
}
