package com.fitness.companion.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp

/**
 * Bar chart drawn directly on a Compose canvas.
 *
 * @param values one bar per entry
 * @param labels x-axis label per bar; an empty string leaves that bar unlabelled
 * @param description spoken summary for screen readers, since the canvas itself is not readable
 */
@Composable
fun BarChart(
    values: List<Int>,
    labels: List<String>,
    barColor: Color,
    description: String,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .semantics { contentDescription = description },
    ) {
        if (values.isEmpty()) return@Canvas

        val maxValue = values.max().coerceAtLeast(1)
        val topPadding = 20.dp.toPx()
        val labelHeight = 20.dp.toPx()
        val chartHeight = size.height - topPadding - labelHeight
        val baselineY = topPadding + chartHeight
        val slotWidth = size.width / values.size
        val barWidth = slotWidth * 0.6f
        val gridStroke = 1.dp.toPx()

        // Dashed line marking the largest value, plus the solid baseline.
        drawLine(
            color = gridColor,
            start = Offset(0f, topPadding),
            end = Offset(size.width, topPadding),
            strokeWidth = gridStroke,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
        )
        drawLine(gridColor, Offset(0f, baselineY), Offset(size.width, baselineY), gridStroke)
        drawText(textMeasurer.measure(maxValue.toString(), labelStyle), topLeft = Offset.Zero)

        values.forEachIndexed { index, value ->
            val slotStart = slotWidth * index
            if (value > 0) {
                val barHeight = chartHeight * value / maxValue
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(slotStart + (slotWidth - barWidth) / 2, baselineY - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(minOf(barWidth / 4, 6.dp.toPx())),
                )
            }
            val label = labels.getOrNull(index).orEmpty()
            if (label.isNotEmpty()) {
                val measured = textMeasurer.measure(label, labelStyle)
                drawText(
                    measured,
                    topLeft = Offset(
                        slotStart + (slotWidth - measured.size.width) / 2,
                        baselineY + 4.dp.toPx(),
                    ),
                )
            }
        }
    }
}
