package com.soundguard.app.ui.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun CoachScreen(
    modifier: Modifier = Modifier,
    viewModel: CoachViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        CoachHeader(
            voiceEnabled = state.voiceEnabled,
            isSpeaking = state.speakingMessageId != null,
            onStopSpeak = viewModel::stopSpeaking,
            onReset = viewModel::resetConversation
        )
        if (!state.isConfigured) {
            ConfigurationRequiredCard(modifier = Modifier.padding(20.dp))
        } else {
            val listState = rememberLazyListState()
            LaunchedEffect(state.messages.size) {
                if (state.messages.isNotEmpty()) {
                    listState.animateScrollToItem(state.messages.size - 1)
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        isPlaying = state.speakingMessageId == "coach-${message.id}",
                        onTogglePlay = { viewModel.toggleSpeak(message) }
                    )
                }
            }
            QuickPromptRow(
                prompts = state.suggestedPrompts,
                isResponding = state.isResponding,
                onPick = { viewModel.send(it) }
            )
            Spacer(Modifier.height(8.dp))
            ChatInputBar(
                isResponding = state.isResponding,
                onSend = { viewModel.send(it) }
            )
        }
    }
}

@Composable
private fun CoachHeader(
    voiceEnabled: Boolean,
    isSpeaking: Boolean,
    onStopSpeak: () -> Unit,
    onReset: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("AI Coach", style = MaterialTheme.typography.headlineMedium)
            val subtitle = when {
                isSpeaking -> "Reading aloud · tap stop to interrupt"
                voiceEnabled -> "Voice replies on · grounded on your alerts"
                else -> "Powered by Gemini · grounded on your alerts"
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isSpeaking) {
            IconButton(onClick = onStopSpeak) {
                Icon(Icons.Filled.Stop, contentDescription = "Stop reading aloud")
            }
        }
        IconButton(onClick = onReset) {
            Icon(Icons.Filled.Refresh, contentDescription = "Reset conversation")
        }
    }
}

@Composable
private fun MessageBubble(
    message: CoachMessage,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit
) {
    val isUser = message.role == MessageRole.USER
    val container = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val onContainer = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface
    val shape = if (isUser) {
        RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
    } else {
        RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .padding(end = if (isUser) 0.dp else 40.dp, start = if (isUser) 40.dp else 0.dp)
                .background(container, shape)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            val displayText = message.text.ifBlank { if (message.isStreaming) "…" else "" }
            Text(
                text = displayText,
                color = onContainer,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (!isUser && !message.isStreaming && message.text.isNotBlank()) {
            Spacer(Modifier.width(6.dp))
            val tint = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (isPlaying) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            ) {
                IconButton(onClick = onTogglePlay, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Stop else Icons.Filled.VolumeUp,
                        contentDescription = if (isPlaying) "Stop reading" else "Read aloud",
                        tint = tint,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickPromptRow(
    prompts: List<String>,
    isResponding: Boolean,
    onPick: (String) -> Unit
) {
    if (prompts.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        prompts.forEach { prompt ->
            AssistChip(
                onClick = { if (!isResponding) onPick(prompt) },
                label = { Text(prompt) },
                enabled = !isResponding
            )
        }
    }
}

@Composable
private fun ChatInputBar(
    isResponding: Boolean,
    onSend: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Ask the coach…") },
            enabled = !isResponding,
            shape = RoundedCornerShape(24.dp),
            maxLines = 3
        )
        Spacer(Modifier.width(8.dp))
        FilledIconButton(
            onClick = {
                val toSend = text.trim()
                if (toSend.isNotEmpty()) {
                    text = ""
                    onSend(toSend)
                }
            },
            enabled = !isResponding && text.isNotBlank(),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.size(52.dp)
        ) {
            if (isResponding) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
private fun ConfigurationRequiredCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Coach unavailable", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Add a Gemini API key (GEMINI_API_KEY=…) to your " +
                    "local.properties to enable the coach. Get a free key at " +
                    "aistudio.google.com/apikey.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
