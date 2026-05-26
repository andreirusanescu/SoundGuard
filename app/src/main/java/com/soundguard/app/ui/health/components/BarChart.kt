package com.soundguard.app.ui.health.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Simple bar chart for the last [values].size days.
 * `values[0]` is oldest, `values.last()` is today.
 */
@Composable
fun DailyBarChart(
    values: List<Int>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    val maxValue = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    val labels = remember(values.size) { dayLabels(values.size) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            values.forEachIndexed { index, value ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        text = if (value > 0) value.toString() else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    val ratio = value.toFloat() / maxValue
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((140 * ratio.coerceIn(0.04f, 1f)).dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxWidth().height((140 * ratio.coerceIn(0.04f, 1f)).dp)) {
                            drawRoundRect(
                                brush = if (value == 0) {
                                    Brush.verticalGradient(listOf(trackColor, trackColor))
                                } else {
                                    Brush.verticalGradient(
                                        listOf(barColor.copy(alpha = 0.85f), barColor)
                                    )
                                },
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f)
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            labels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

private fun dayLabels(count: Int): List<String> {
    val fmt = SimpleDateFormat("EEE", Locale.getDefault())
    val cal = Calendar.getInstance()
    val labels = mutableListOf<String>()
    for (i in (count - 1) downTo 0) {
        val c = cal.clone() as Calendar
        c.add(Calendar.DAY_OF_YEAR, -i)
        labels.add(fmt.format(c.time))
    }
    return labels
}

@Composable
private fun <T> remember(key: Any?, calculation: () -> T): T =
    androidx.compose.runtime.remember(key, calculation)
