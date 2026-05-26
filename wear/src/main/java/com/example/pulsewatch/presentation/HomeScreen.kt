package com.example.pulsewatch.presentation

import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.example.pulsewatch.safety.AlertApiClient
import com.example.pulsewatch.safety.health.BiometricMonitor
import kotlinx.coroutines.launch

@Composable
fun WearHomeScreen(
    listening: Boolean,
    todayAlerts: Int,
    ambientDb: Int?,
    onToggleListener: () -> Unit,
    onOpenContacts: () -> Unit,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    AppScaffold {
        ScreenScaffold(scrollState = scrollState) { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(contentPadding)
                    .padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BrandHeader()
                HeroStatusCard(listening = listening)
                TodayStatsCard(alerts = todayAlerts, ambientDb = ambientDb)
                SosQuickRow(
                    onSosTap = { triggerSos(context, alertLabel = null, auto = false) }
                )
                ZoneCheckCard()
                TrustedContactsButton(onClick = onOpenContacts)
                PrimaryToggleButton(listening = listening, onClick = onToggleListener)
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun BrandHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "SoundGuard",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Hear what matters",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroStatusCard(listening: Boolean) {
    val container = if (listening)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceContainer
    val onContainer = if (listening)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurface

    val demoArmed by BiometricMonitor.isDemoArmed.collectAsState()
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    val subtitle = when {
        demoArmed -> "🩸 SpO2 SIM armed · long-press"
        listening -> "Mic active · Streaming"
        else -> "Tap Start to protect"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(container)
            // combinedClickable plays nicely with the parent verticalScroll —
            // detectTapGestures inside pointerInput tends to lose to the scroll
            // on Wear OS, so the long-press never fires. This path uses the
            // standard touch-slop disambiguation that Foundation provides.
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { /* nothing on tap */ },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    BiometricMonitor.toggleDemoArmed()
                },
            )
            .padding(vertical = 12.dp, horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ListeningOrb(active = listening, accent = onContainer)
            Spacer(modifier = Modifier.size(10.dp))
            Column {
                Text(
                    text = if (listening) "Listening" else "Idle",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = onContainer,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = onContainer.copy(alpha = 0.78f),
                )
            }
        }
    }
}

@Composable
private fun ListeningOrb(active: Boolean, accent: Color) {
    if (!active) {
        // Static dot — no animations when idle, keeps recomposition cheap.
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(50))
                .background(accent.copy(alpha = 0.6f)),
        )
        return
    }

    val pulse = rememberInfiniteTransition(label = "orb")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orbScale",
    )
    val ringAlpha by pulse.animateFloat(
        initialValue = 0.55f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orbAlpha",
    )
    val ringRadius by pulse.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orbRing",
    )

    Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(36.dp)) {
            drawCircle(
                color = accent.copy(alpha = ringAlpha),
                radius = (size.minDimension / 2f) * ringRadius,
                style = Stroke(width = 2.5.dp.toPx()),
            )
        }
        Box(
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer(scaleX = scale, scaleY = scale)
                .clip(RoundedCornerShape(50))
                .background(accent),
        )
    }
}

@Composable
private fun TodayStatsCard(alerts: Int, ambientDb: Int?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatCell(
            emoji = "🚨",
            value = alerts.toString(),
            label = "today",
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(width = 1.dp, height = 28.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        )
        StatCell(
            emoji = "🔊",
            value = ambientDb?.let { "$it" } ?: "—",
            label = if (ambientDb != null) "dB now" else "ambient",
            modifier = Modifier.weight(1f),
            valueWarn = ambientDb != null && ambientDb >= 85,
        )
    }
}

@Composable
private fun StatCell(
    emoji: String,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueWarn: Boolean = false,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = emoji, fontSize = 14.sp)
            Spacer(modifier = Modifier.size(3.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (valueWarn) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SosQuickRow(onSosTap: () -> Unit) {
    Button(
        onClick = onSosTap,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "📞", fontSize = 18.sp)
            Spacer(modifier = Modifier.size(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Quick SOS",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Start,
                )
                Text(
                    text = "Emails contact + GPS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.78f),
                )
            }
            Text(
                text = "›",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private sealed class ZoneState {
    object Idle : ZoneState()
    object Checking : ZoneState()
    data class Done(val isDangerous: Boolean, val nearby: Int) : ZoneState()
    data class Error(val message: String) : ZoneState()
}

@Composable
private fun ZoneCheckCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<ZoneState>(ZoneState.Idle) }

    val container = when (val s = state) {
        is ZoneState.Done -> if (s.isDangerous)
            MaterialTheme.colorScheme.errorContainer
        else
            MaterialTheme.colorScheme.surfaceContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val onContainer = when (val s = state) {
        is ZoneState.Done -> if (s.isDangerous)
            MaterialTheme.colorScheme.onErrorContainer
        else
            MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurface
    }

    Button(
        onClick = {
            if (state is ZoneState.Checking) return@Button
            state = ZoneState.Checking
            scope.launch {
                state = when (val r = AlertApiClient.checkLocation(context)) {
                    is AlertApiClient.CheckResult.Done ->
                        ZoneState.Done(r.isDangerous, r.nearbyAlertsCount)
                    is AlertApiClient.CheckResult.Error -> ZoneState.Error(r.message)
                    AlertApiClient.CheckResult.NoLocation -> ZoneState.Error("No GPS")
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = onContainer,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            val emoji = when (val s = state) {
                is ZoneState.Done -> if (s.isDangerous) "⚠️" else "✅"
                is ZoneState.Error -> "❓"
                ZoneState.Checking -> "⏳"
                ZoneState.Idle -> "📍"
            }
            Text(text = emoji, fontSize = 16.sp)
            Spacer(modifier = Modifier.size(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Zone check",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Start,
                )
                Text(
                    text = when (val s = state) {
                        ZoneState.Idle -> "Tap to scan"
                        ZoneState.Checking -> "Checking…"
                        is ZoneState.Done ->
                            if (s.isDangerous) "Dangerous · ${s.nearby} nearby"
                            else "Clear · ${s.nearby} nearby"
                        is ZoneState.Error -> s.message
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = onContainer.copy(alpha = 0.78f),
                )
            }
        }
    }
}

@Composable
private fun TrustedContactsButton(onClick: () -> Unit) {
    val contacts by com.example.pulsewatch.data.TrustedContactsStore.contacts.collectAsState()
    val subtitle = when (contacts.size) {
        0 -> "None set · add one"
        1 -> "1 person on SOS list"
        else -> "${contacts.size} on SOS list"
    }
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "👥", fontSize = 16.sp)
            Spacer(modifier = Modifier.size(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Trusted contacts",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Start,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(text = "›", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PrimaryToggleButton(listening: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = if (listening)
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
        else
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = if (listening) "⏸" else "▶", fontSize = 16.sp)
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = if (listening) "Stop SoundGuard" else "Start SoundGuard",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
