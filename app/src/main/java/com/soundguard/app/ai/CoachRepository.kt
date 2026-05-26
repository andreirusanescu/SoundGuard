package com.soundguard.app.ai

import com.soundguard.app.data.HealthRepository
import com.soundguard.app.data.HealthSummary
import com.soundguard.app.data.HearingProfile
import com.soundguard.app.data.PreferencesRepository
import com.soundguard.app.data.db.AlertEventDao
import com.soundguard.app.ml.SoundCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** Cross-screen request to switch to the Coach tab and auto-send a prompt. */
data class CoachIntent(val prompt: String, val origin: String = "app")

@Singleton
class CoachRepository @Inject constructor(
    private val gemini: GeminiClient,
    private val health: HealthRepository,
    private val prefs: PreferencesRepository,
    private val alertDao: AlertEventDao
) {
    val isConfigured: Boolean get() = gemini.isConfigured

    private val _intents = MutableSharedFlow<CoachIntent>(replay = 1)
    /** Emitted by Home/Health/Settings when they want the Coach to handle a prompt. */
    val intents: Flow<CoachIntent> = _intents.asSharedFlow()

    /** Streams a coach reply grounded on profile + summary + recent alerts + chat history. */
    suspend fun replyStream(history: List<ChatTurn>): Flow<String> {
        val summary = currentSummary()
        val profile = prefs.profile.firstOrNull() ?: HearingProfile()
        val recent = recentAlerts()
        return gemini.chatStream(history, CoachContext(summary, profile, recent))
    }

    /** One-shot reply (no chat history) — used by Health insight and Daily Briefing. */
    suspend fun oneShot(userPrompt: String): Flow<String> =
        replyStream(listOf(ChatTurn(ChatTurn.USER, userPrompt)))

    suspend fun submitIntent(prompt: String, origin: String = "app") {
        _intents.emit(CoachIntent(prompt, origin))
    }

    fun consumeIntentReplay() {
        _intents.resetReplayCache()
    }

    private suspend fun currentSummary(): HealthSummary =
        health.summary.firstOrNull() ?: HealthSummary(0, 0, null, null, List(7) { 0 })

    private suspend fun recentAlerts(): List<RecentAlert> {
        val rows = alertDao.observeRecent().firstOrNull().orEmpty()
        val now = System.currentTimeMillis()
        return rows.take(5).mapNotNull { row ->
            val cat = runCatching { SoundCategory.valueOf(row.category) }.getOrNull() ?: return@mapNotNull null
            RecentAlert(
                displayName = cat.displayName,
                severity = cat.severity.name,
                minutesAgo = ((now - row.timestampMs) / 60_000L).coerceAtLeast(0L)
            )
        }
    }
}
