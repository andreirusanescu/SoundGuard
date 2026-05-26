package com.example.pulsewatch.presentation

import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.example.pulsewatch.data.SOS_ESCALATION_SECONDS
import com.example.pulsewatch.data.SOS_FAST_ESCALATION_SECONDS
import com.example.pulsewatch.data.SoundAlertPayload
import com.example.pulsewatch.data.SoundDirection
import com.example.pulsewatch.data.SoundSeverity
import kotlinx.coroutines.delay

private val ColorCritical = Color(0xFFD32F2F)
private val ColorHigh = Color(0xFFE64A19)
private val ColorMedium = Color(0xFFF57F17)

@Composable
fun SoundAlertScreen(
    payload: SoundAlertPayload,
    fastEscalate: Boolean,
    onDismiss: () -> Unit,
    onTimeoutEscalate: () -> Unit,
) {
    val context = LocalContext.current
    val bgColor = when (payload.severity) {
        SoundSeverity.CRITICAL -> ColorCritical
        SoundSeverity.HIGH -> ColorHigh
        SoundSeverity.MEDIUM -> ColorMedium
    }

    val totalSec = if (fastEscalate) SOS_FAST_ESCALATION_SECONDS else SOS_ESCALATION_SECONDS

    // Countdown state — anchored on payload.timestamp so a new alert resets it.
    var secondsLeft by remember(payload.timestamp) {
        mutableIntStateOf(totalSec)
    }
    var resolved by remember(payload.timestamp) { mutableStateOf(false) }

    LaunchedEffect(payload.timestamp) {
        for (s in totalSec - 1 downTo 0) {
            delay(1_000)
            secondsLeft = s
        }
        if (!resolved) {
            resolved = true
            onTimeoutEscalate()
        }
    }

    val pulse = rememberInfiniteTransition(label = "pulse")

    val iconScale by pulse.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconScale"
    )

    val radarRadius by pulse.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarRadius"
    )

    val radarAlpha by pulse.animateFloat(
        initialValue = 0.7f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        // Three vertical zones distributed across the round screen so nothing
        // gets clipped by the curved edges. Horizontal padding keeps content
        // inside the inscribed safe rectangle.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── TOP: emoji + label ────────────────────────────────────
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = payload.type.emoji,
                    fontSize = 28.sp,
                    modifier = Modifier.graphicsLayer(scaleX = iconScale, scaleY = iconScale)
                )
                Text(
                    text = payload.type.label.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }

            // ── MIDDLE: direction + confidence + countdown ───────────
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                DirectionIndicator(
                    direction = payload.direction,
                    radarRadius = radarRadius,
                    radarAlpha = radarAlpha,
                )
                Spacer(modifier = Modifier.height(3.dp))
                ConfidenceChip(confidence = payload.confidence)
                Spacer(modifier = Modifier.height(3.dp))
                EscalationCountdown(secondsLeft = secondsLeft, fastEscalate = fastEscalate)
            }

            // ── BOTTOM: Cancel (icon) + Send SOS (compact) ───────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        if (!resolved) {
                            resolved = true
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Black.copy(alpha = 0.50f),
                        contentColor = Color.White,
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "✕",
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                Button(
                    onClick = {
                        if (!resolved) {
                            resolved = true
                            triggerSos(
                                context = context,
                                alertLabel = payload.type.label,
                                auto = false,
                                biometricSpike = fastEscalate,
                            )
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFFB00020),
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "✉️ SOS",
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun EscalationCountdown(secondsLeft: Int, fastEscalate: Boolean) {
    val warning = secondsLeft <= 3
    val bgAlpha = when {
        fastEscalate -> 0.55f
        warning -> 0.42f
        else -> 0.22f
    }
    val text = when {
        fastEscalate && secondsLeft > 0 -> "🩸 LOW SpO2 · SOS in ${secondsLeft}s"
        fastEscalate -> "🩸 Sending SOS…"
        secondsLeft > 0 -> "📡 SOS in ${secondsLeft}s"
        else -> "📡 Sending…"
    }
    val color = when {
        fastEscalate -> Color(0xFFFFCDD2)
        warning -> Color(0xFFFFE082)
        else -> Color.White
    }
    Box(
        modifier = Modifier
            .background(
                color = Color.Black.copy(alpha = bgAlpha),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
            )
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}


@Composable
private fun ConfidenceChip(confidence: Float) {
    val pct = (confidence.coerceIn(0f, 1f) * 100f).toInt()
    Box(
        modifier = Modifier
            .background(
                color = Color.Black.copy(alpha = 0.28f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
            )
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = "${pct}% confidence",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White
        )
    }
}

@Composable
private fun DirectionIndicator(
    direction: SoundDirection,
    radarRadius: Float,
    radarAlpha: Float
) {
    Canvas(modifier = Modifier.size(42.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val pivot = Offset(cx, cy)
        val maxR = size.minDimension / 2f

        // Animated radar pulse ring
        drawCircle(
            color = Color.White.copy(alpha = radarAlpha * 0.55f),
            radius = maxR * radarRadius,
            center = pivot,
            style = Stroke(width = 2.5.dp.toPx())
        )

        // Direction arrow
        val arrowDeg = when (direction) {
            SoundDirection.LEFT -> 180f
            SoundDirection.RIGHT -> 0f
            SoundDirection.CENTER -> 270f // pointing up = all around
        }

        if (direction == SoundDirection.CENTER) {
            // Cross/compass for center
            for (deg in listOf(0f, 90f, 180f, 270f)) {
                rotate(deg, pivot) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.85f),
                        start = Offset(cx + maxR * 0.18f, cy),
                        end = Offset(cx + maxR * 0.72f, cy),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        } else {
            rotate(arrowDeg, pivot) {
                val tipX = cx + maxR * 0.72f
                val tailX = cx - maxR * 0.10f
                val headX = cx + maxR * 0.35f
                val headY = maxR * 0.32f

                // Shaft
                drawLine(
                    color = Color.White,
                    start = Offset(tailX, cy),
                    end = Offset(tipX, cy),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )
                // Arrowhead top
                drawLine(
                    color = Color.White,
                    start = Offset(tipX, cy),
                    end = Offset(headX, cy - headY),
                    strokeWidth = 3.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
                // Arrowhead bottom
                drawLine(
                    color = Color.White,
                    start = Offset(tipX, cy),
                    end = Offset(headX, cy + headY),
                    strokeWidth = 3.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
