package com.soundguard.app.ui.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soundguard.app.ai.ChatTurn
import com.soundguard.app.ai.CoachRepository
import com.soundguard.app.data.PreferencesRepository
import com.soundguard.app.voice.AssistantSpeaker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class MessageRole { USER, COACH }

data class CoachMessage(
    val id: Long,
    val role: MessageRole,
    val text: String,
    val isStreaming: Boolean = false
)

data class CoachUiState(
    val messages: List<CoachMessage> = emptyList(),
    val isResponding: Boolean = false,
    val isConfigured: Boolean = true,
    val voiceEnabled: Boolean = false,
    val speakingMessageId: String? = null,
    val suggestedPrompts: List<String> = emptyList()
)

@HiltViewModel
class CoachViewModel @Inject constructor(
    private val repository: CoachRepository,
    private val speaker: AssistantSpeaker,
    prefs: PreferencesRepository
) : ViewModel() {

    private val voiceEnabled: StateFlow<Boolean> = prefs.accessibility
        .map { it.voiceReplies }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _baseState = MutableStateFlow(
        CoachUiState(
            isConfigured = repository.isConfigured,
            messages = if (repository.isConfigured) {
                listOf(
                    CoachMessage(
                        id = nextId(),
                        role = MessageRole.COACH,
                        text = "Hi! I'm your SoundGuard coach. Ask me about an alert, hearing care, or how to use any feature. Add a profile in Settings and I'll tailor my answers to you."
                    )
                )
            } else emptyList(),
            suggestedPrompts = DEFAULT_PROMPTS
        )
    )

    val state: StateFlow<CoachUiState> = combine(
        _baseState,
        voiceEnabled,
        speaker.speakingId
    ) { base, voice, speakingId ->
        base.copy(voiceEnabled = voice, speakingMessageId = speakingId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _baseState.value)

    private var streamJob: Job? = null
    private var lastId: Long = 0L

    init {
        // Listen for cross-screen prompts (e.g. Home Emergency card sends "What should I do?").
        viewModelScope.launch {
            repository.intents.collect { intent ->
                send(intent.prompt)
                repository.consumeIntentReplay()
            }
        }
    }

    fun send(prompt: String) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty() || _baseState.value.isResponding) return

        val userMsg = CoachMessage(nextId(), MessageRole.USER, trimmed)
        val coachMsg = CoachMessage(nextId(), MessageRole.COACH, "", isStreaming = true)

        // Snapshot history *before* the streaming placeholder is added — Gemini needs
        // the user's just-sent message at the tail of the conversation.
        val history = (_baseState.value.messages + userMsg).map { m ->
            val role = if (m.role == MessageRole.USER) ChatTurn.USER else ChatTurn.ASSISTANT
            ChatTurn(role, m.text)
        }

        _baseState.update {
            it.copy(
                messages = it.messages + userMsg + coachMsg,
                isResponding = true
            )
        }

        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            val builder = StringBuilder()
            runCatching {
                repository.replyStream(history).collect { delta ->
                    builder.append(delta)
                    _baseState.update { current ->
                        current.copy(
                            messages = current.messages.map { m ->
                                if (m.id == coachMsg.id) m.copy(text = builder.toString()) else m
                            }
                        )
                    }
                }
            }.onFailure { e ->
                builder.append("\n\n_(Coach error: ${e.message ?: "unknown"})_")
            }
            val finalText = builder.toString()
            _baseState.update { current ->
                current.copy(
                    isResponding = false,
                    messages = current.messages.map { m ->
                        if (m.id == coachMsg.id) m.copy(text = finalText, isStreaming = false) else m
                    },
                    suggestedPrompts = followUpPrompts(finalText)
                )
            }
            if (voiceEnabled.value && finalText.isNotBlank()) {
                speaker.speak(finalText, id = "coach-${coachMsg.id}")
            }
        }
    }

    /** Resets the conversation to the initial greeting. */
    fun resetConversation() {
        speaker.stop()
        streamJob?.cancel()
        _baseState.update {
            it.copy(
                messages = if (it.isConfigured) listOf(
                    CoachMessage(
                        id = nextId(),
                        role = MessageRole.COACH,
                        text = "Fresh start. What can I help you with?"
                    )
                ) else emptyList(),
                isResponding = false,
                suggestedPrompts = DEFAULT_PROMPTS
            )
        }
    }

    /** Manually replay a coach message via TTS (independent of voiceReplies setting). */
    fun toggleSpeak(message: CoachMessage) {
        if (message.role != MessageRole.COACH || message.text.isBlank()) return
        val ttsId = "coach-${message.id}"
        if (speaker.speakingId.value == ttsId) {
            speaker.stop()
        } else {
            speaker.speak(message.text, id = ttsId)
        }
    }

    fun stopSpeaking() = speaker.stop()

    override fun onCleared() {
        speaker.stop()
        super.onCleared()
    }

    private fun nextId(): Long {
        lastId += 1
        return lastId
    }

    private fun followUpPrompts(lastReply: String): List<String> {
        val lower = lastReply.lowercase()
        return when {
            "siren" in lower || "police" in lower || "ambulance" in lower -> listOf(
                "What should I do during a siren?",
                "How do I lower my own stress fast?",
                "Mute non-critical alerts for 1 hour"
            )
            "fire" in lower || "alarm" in lower -> listOf(
                "Walk me through a fire-alarm checklist",
                "How do I help others nearby?",
                "Show me the safest exit pattern"
            )
            "ear" in lower || "hearing" in lower || "auz" in lower -> listOf(
                "Suggest a 5-minute quiet break routine",
                "How can I tell if my volume is comfortable?",
                "Tips for sleeping in a noisy area"
            )
            else -> DEFAULT_PROMPTS
        }
    }

    private companion object {
        val DEFAULT_PROMPTS = listOf(
            "What was my last alert?",
            "Suggest a quiet break routine",
            "Explain RO-ALERT",
            "Tailor advice for my profile"
        )
    }
}
