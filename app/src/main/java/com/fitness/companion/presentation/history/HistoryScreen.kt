package com.fitness.companion.presentation.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitness.companion.domain.model.DayActivity
import com.fitness.companion.domain.model.HistoryData
import com.fitness.companion.domain.model.HistoryPeriod
import com.fitness.companion.domain.model.WorkoutSession
import com.fitness.companion.domain.model.WorkoutSource
import com.fitness.companion.domain.usecase.ObserveHistoryUseCase
import com.fitness.companion.domain.util.toLocalDate
import com.fitness.companion.presentation.components.BarChart
import com.fitness.companion.presentation.util.APP_LOCALE
import com.fitness.companion.presentation.util.formatCount
import com.fitness.companion.presentation.util.formatDuration
import com.fitness.companion.presentation.util.formatQuantity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    observeHistory: ObserveHistoryUseCase,
) : ViewModel() {

    private val _period = MutableStateFlow(HistoryPeriod.WEEK)
    val period: StateFlow<HistoryPeriod> = _period.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val history: StateFlow<HistoryData?> = _period
        .flatMapLatest { observeHistory(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectPeriod(period: HistoryPeriod) {
        _period.value = period
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val period by viewModel.period.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            HistoryPeriod.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = period == option,
                    onClick = { viewModel.selectPeriod(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, HistoryPeriod.entries.size),
                ) {
                    Text(option.label)
                }
            }
        }

        val data = history ?: return@Column
        val labels = axisLabels(data.days, period)

        ChartCard(
            title = "Bước chân",
            summary = "Tổng ${formatCount(data.totalSteps)} · " +
                "trung bình ${formatCount(data.averageSteps)} mỗi ngày",
            values = data.days.map { it.steps },
            labels = labels,
            color = MaterialTheme.colorScheme.primary,
        )
        ChartCard(
            title = "Số lần tập",
            summary = "${formatQuantity(data.totalReps, "lần")} trong " +
                formatQuantity(data.sessions.size, "buổi tập"),
            values = data.days.map { it.reps },
            labels = labels,
            color = MaterialTheme.colorScheme.tertiary,
        )
        WorkoutList(data.sessions)
    }
}

@Composable
private fun ChartCard(
    title: String,
    summary: String,
    values: List<Int>,
    labels: List<String>,
    color: Color,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BarChart(
                values = values,
                labels = labels,
                barColor = color,
                description = "Biểu đồ $title. $summary",
            )
        }
    }
}

@Composable
private fun WorkoutList(sessions: List<WorkoutSession>) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Buổi tập", style = MaterialTheme.typography.titleMedium)
            if (sessions.isEmpty()) {
                Text(
                    "Chưa có buổi tập nào trong khoảng thời gian này.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            sessions.take(MAX_LISTED_WORKOUTS).forEachIndexed { index, session ->
                if (index > 0) HorizontalDivider()
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${session.exerciseType.displayName} · ${formatQuantity(session.reps, "lần")}",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            session.startTime.toLocalDate().format(DATE_FORMAT) + " · " +
                                if (session.source == WorkoutSource.VIDEO_GUIDE) "Video hướng dẫn" else "Bộ đếm",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        formatDuration(session.duration.toMillis()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

/** Weekday names for the weekly chart; every fifth day of the month for the monthly one. */
private fun axisLabels(days: List<DayActivity>, period: HistoryPeriod): List<String> =
    days.mapIndexed { index, day ->
        when {
            period == HistoryPeriod.WEEK ->
                day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, APP_LOCALE)
            (days.lastIndex - index) % 5 == 0 -> day.date.dayOfMonth.toString()
            else -> ""
        }
    }

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", APP_LOCALE)
private const val MAX_LISTED_WORKOUTS = 20
