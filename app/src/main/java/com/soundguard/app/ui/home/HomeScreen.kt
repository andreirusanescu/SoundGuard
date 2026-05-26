package com.soundguard.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soundguard.app.ml.Detection
import com.soundguard.app.ml.Severity
import com.soundguard.app.ml.SoundCategory
import com.soundguard.app.ui.theme.BrandIndigo
import com.soundguard.app.ui.theme.BrandIndigoDeep
import com.soundguard.app.ui.theme.BrandViolet
import com.soundguard.app.ui.theme.iconFor
import com.soundguard.app.ui.theme.paletteFor
import com.soundguard.app.ui.theme.severityLabel

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        AnimatedVisibility(
            visible = state.emergencyDetection != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            state.emergencyDetection?.let { detection ->
                EmergencyAiCard(
                    detection = detection,
                    onAskCoach = { viewModel.askCoachAboutEmergency(detection) },
                    onCall112 = viewModel::call112,
                    onShare = { viewModel.shareSafetyMessage(detection) }
                )
                Spacer(Modifier.height(20.dp))
            }
        }
        HeroCard(isListening = state.isListening)
        Spacer(Modifier.height(20.dp))
        AnimatedVisibility(
            visible = state.lastDetection != null && state.emergencyDetection == null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            state.lastDetection?.let { LastDetectionCard(it) }
        }
        Spacer(Modifier.height(20.dp))
        RoAlertCard(
            listenerEnabled = state.isRoAlertListenerEnabled,
            onGrantClick = viewModel::openListenerSettings,
            onTestClick = viewModel::sendTestRoAlert
        )
        Spacer(Modifier.height(20.dp))
        LocationDangerCard(
            state = state.locationCheck,
            onCheckClick = viewModel::checkLocationDanger,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Test alerts",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Text(
            text = "Send a synthetic alert to your watch right now.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Skip RO_ALERT here — it has its own dedicated card above.
            SoundCategory.values()
                .filter { it != SoundCategory.RO_ALERT }
                .forEach { cat ->
                    CategoryChip(category = cat, onClick = { viewModel.sendTestAlert(cat) })
                }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun HeroCard(isListening: Boolean) {
    val brush = Brush.linearGradient(
        colors = if (isListening) {
            listOf(BrandIndigoDeep, BrandIndigo, BrandViolet)
        } else {
            listOf(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.surface
            )
        }
    )
    val onSurface = if (isListening) Color.White else MaterialTheme.colorScheme.onSurface

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(brush)
                .padding(24.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PulsingDot(active = isListening, color = if (isListening) Color.White else MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (isListening) "LIVE" else "OFFLINE",
                        style = MaterialTheme.typography.labelMedium,
                        color = onSurface.copy(alpha = 0.85f)
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = if (isListening) "Streaming from\nyour watch" else "Waiting for\nyour watch…",
                    style = MaterialTheme.typography.headlineLarge,
                    color = onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (isListening) {
                        "Audio chunks arrive every 500 ms. We're classifying each one on-device with YAMNet."
                    } else {
                        "Open the SoundGuard app on your Pixel Watch and tap Start to begin streaming."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = onSurface.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun PulsingDot(active: Boolean, color: Color) {
    val scale = if (active) {
        val transition = rememberInfiniteTransition(label = "pulse")
        val animated by transition.animateFloat(
            initialValue = 0.7f,
            targetValue = 1.4f,
            animationSpec = infiniteRepeatable(tween(900), repeatMode = RepeatMode.Reverse),
            label = "scale"
        )
        animated
    } else 1f
    Box(
        modifier = Modifier
            .size(10.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun LastDetectionCard(detection: Detection) {
    val palette = paletteFor(detection.category.severity)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = palette.container)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(palette.accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconFor(detection.category),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "JUST DETECTED · ${severityLabel(detection.category.severity).uppercase()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.onContainer.copy(alpha = 0.75f)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = detection.category.displayName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = palette.onContainer
                )
                Text(
                    text = "${(detection.score * 100).toInt()}% confidence",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.onContainer.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun RoAlertCard(
    listenerEnabled: Boolean,
    onGrantClick: () -> Unit,
    onTestClick: () -> Unit
) {
    val palette = paletteFor(Severity.CRITICAL)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = palette.container)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(palette.accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Campaign,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "RO-ALERT",
                        style = MaterialTheme.typography.titleLarge,
                        color = palette.onContainer
                    )
                    Text(
                        text = if (listenerEnabled) "Connected" else "Not connected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.onContainer.copy(alpha = 0.85f)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (listenerEnabled) {
                    "Romanian national emergency cell-broadcast alerts will be forwarded to " +
                        "your watch with the highest-priority haptic pattern."
                } else {
                    "Grant SoundGuard notification access so we can intercept RO-ALERT " +
                        "cell-broadcasts and relay them to your watch."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onContainer.copy(alpha = 0.85f)
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!listenerEnabled) {
                    Button(
                        onClick = onGrantClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.accent,
                            contentColor = Color.White
                        )
                    ) { Text("Grant access") }
                }
                TextButton(
                    onClick = onTestClick,
                    colors = ButtonDefaults.textButtonColors(contentColor = palette.onContainer)
                ) { Text("Send test RO-ALERT") }
            }
        }
    }
}

@Composable
private fun EmergencyAiCard(
    detection: Detection,
    onAskCoach: () -> Unit,
    onCall112: () -> Unit,
    onShare: () -> Unit
) {
    val palette = paletteFor(Severity.CRITICAL)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = palette.container)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(palette.accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = iconFor(detection.category),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "EMERGENCY · ${severityLabel(Severity.CRITICAL).uppercase()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.onContainer.copy(alpha = 0.85f)
                    )
                    Text(
                        text = detection.category.displayName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = palette.onContainer
                    )
                    Text(
                        text = "${(detection.score * 100).toInt()}% confidence — pick a fast action below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.onContainer.copy(alpha = 0.85f)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onAskCoach,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.accent,
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Ask AI: what should I do?")
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onCall112,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Call 112")
                }
                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share check-in")
                }
            }
        }
    }
}

@Composable
private fun LocationDangerCard(
    state: LocationCheckState,
    onCheckClick: () -> Unit,
) {
    val palette = when (state) {
        is LocationCheckState.Done ->
            if (state.isDangerous) paletteFor(Severity.HIGH) else paletteFor(Severity.MEDIUM)
        is LocationCheckState.Error -> paletteFor(Severity.MEDIUM)
        else -> paletteFor(Severity.MEDIUM)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = palette.container)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(palette.accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.LocationSearching,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Zone check",
                        style = MaterialTheme.typography.titleLarge,
                        color = palette.onContainer
                    )
                    Text(
                        text = when (state) {
                            LocationCheckState.Idle -> "See if your area has recent alerts"
                            LocationCheckState.Checking -> "Checking…"
                            is LocationCheckState.Done ->
                                if (state.isDangerous) "⚠️ Dangerous · ${state.nearbyAlertsCount} nearby"
                                else "✅ Clear · ${state.nearbyAlertsCount} nearby"
                            is LocationCheckState.Error -> state.message
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.onContainer.copy(alpha = 0.85f)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onCheckClick,
                enabled = state !is LocationCheckState.Checking,
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.accent,
                    contentColor = Color.White
                )
            ) {
                if (state is LocationCheckState.Checking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (state is LocationCheckState.Checking) "Checking…" else "Check my zone")
            }
        }
    }
}

@Composable
private fun CategoryChip(
    category: SoundCategory,
    onClick: () -> Unit
) {
    val palette = paletteFor(category.severity)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = palette.container,
        contentColor = palette.onContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = iconFor(category),
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = category.displayName,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
