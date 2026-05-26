package com.soundguard.app.ai

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.soundguard.app.BuildConfig
import com.soundguard.app.data.HealthSummary
import com.soundguard.app.data.HearingProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

data class ChatTurn(val role: String, val text: String) {
    companion object {
        const val USER = "user"
        const val ASSISTANT = "assistant"
    }
}

data class CoachContext(
    val summary: HealthSummary,
    val profile: HearingProfile,
    val recentAlerts: List<RecentAlert> = emptyList()
)

data class RecentAlert(
    val displayName: String,
    val severity: String,
    val minutesAgo: Long
)

@Singleton
class GeminiClient @Inject constructor() {

    val isConfigured: Boolean get() = BuildConfig.GEMINI_API_KEY.isNotBlank()

    private val model: GenerativeModel? by lazy {
        if (!isConfigured) null else GenerativeModel(
            // gemini-2.5-flash-lite has the largest free-tier daily quota in
            // the 2.5 family (1000 req/day, 15 RPM) — 4× more headroom than
            // 2.5-flash (250/day, 10 RPM). Older 1.5/2.0 models 404 on this
            // API key. Quality is lower than full flash but easily enough
            // for an ≤80-word hearing-care reply.
            modelName = "gemini-2.5-flash-lite",
            apiKey = BuildConfig.GEMINI_API_KEY,
            generationConfig = generationConfig {
                temperature = 0.6f
                topP = 0.95f
                maxOutputTokens = 320
            }
        )
    }

    /**
     * Streams Gemini's reply token-by-token. Each emission is the *delta* string
     * (not the cumulative response), so callers should append. The conversation
     * [history] is the full prior turn list (oldest first); the latest user
     * turn must already be the last entry.
     *
     * On a 429 (per-minute rate limit) the call sleeps [RATE_LIMIT_BACKOFF_MS]
     * and retries once before surfacing a friendly quota message — common case
     * during demos where multiple prompts fire within a minute.
     */
    fun chatStream(
        history: List<ChatTurn>,
        ctx: CoachContext
    ): Flow<String> = flow {
        val m = model ?: run {
            emit("Gemini API key not configured. Add GEMINI_API_KEY to local.properties.")
            return@flow
        }
        val prompt = buildPrompt(history, ctx)
        var attempt = 0
        while (true) {
            try {
                m.generateContentStream(prompt).collect { resp ->
                    resp.text?.let { emit(it) }
                }
                return@flow
            } catch (e: Exception) {
                val msg = e.message.orEmpty()
                val isRateLimit = "429" in msg || "quota" in msg.lowercase() || "rate" in msg.lowercase()
                if (isRateLimit && attempt == 0) {
                    Log.w(TAG, "Gemini 429 — backing off ${RATE_LIMIT_BACKOFF_MS}ms and retrying once")
                    emit("⏳ Rate limit hit — waiting briefly to retry…\n\n")
                    delay(RATE_LIMIT_BACKOFF_MS)
                    attempt++
                    continue
                }
                if (isRateLimit) {
                    emit(
                        "Daily quota reached on the free tier (1000 prompts/day on gemini-2.5-flash-lite). " +
                            "Try again in a minute, or rotate your GEMINI_API_KEY in local.properties."
                    )
                } else {
                    emit("Coach error: ${msg.ifBlank { "unknown" }}")
                }
                Log.e(TAG, "Gemini call failed", e)
                return@flow
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun buildPrompt(history: List<ChatTurn>, ctx: CoachContext): String = buildString {
        appendLine(SYSTEM_PROMPT)
        appendLine()
        appendLine("USER PROFILE:")
        appendLine(ctx.profile.summary())
        appendLine()
        appendLine("LAST 24H STATS:")
        appendLine("- Alerts: ${ctx.summary.total24h}")
        appendLine("- Critical events: ${ctx.summary.criticalCount24h}")
        ctx.summary.topCategory?.let { appendLine("- Most frequent: ${it.displayName}") }
        ctx.summary.lastAlertMs?.let {
            val mins = (System.currentTimeMillis() - it) / 60_000L
            appendLine("- Last alert: $mins min ago")
        }
        if (ctx.recentAlerts.isNotEmpty()) {
            appendLine()
            appendLine("RECENT ALERTS (most recent first):")
            ctx.recentAlerts.take(5).forEach { a ->
                appendLine("- ${a.displayName} (${a.severity}) — ${a.minutesAgo} min ago")
            }
        }
        appendLine()
        appendLine("CONVERSATION:")
        history.forEach { turn ->
            val tag = if (turn.role == ChatTurn.USER) "USER" else "COACH"
            appendLine("$tag: ${turn.text}")
        }
        appendLine("COACH:")
    }

    private companion object {
        const val TAG = "SoundGuard.Gemini"
        const val RATE_LIMIT_BACKOFF_MS = 30_000L

        const val SYSTEM_PROMPT = """
You are SoundGuard, a warm and practical safety + hearing-care coach.

About SoundGuard: a wearable companion for headphone users. The user's smartwatch microphone streams audio to the phone, where YAMNet runs on-device to classify safety-critical sounds (siren, fire alarm, shout for help, car horn, glass breaking, baby crying, dog barking). Alerts are pushed back to the watch with class-specific haptic patterns. The app also relays Romania's RO-ALERT national emergency cell-broadcasts.

You can help with:
- What a specific alert TYPE means and the best in-the-moment reaction
- Practical hearing-care habits — quiet breaks, comfortable volume, sleep environments
- How to use SoundGuard features (sign-in, accessibility, RO-ALERT)
- Calm, focus, and wellbeing routines around sound exposure

Style: warm, concrete, ≤80 words, no medical diagnosis. When a profile or alert history is shared, adapt your reply to that context (mention the user's name once if known, reference relevant alerts). Reply in the user's language (Romanian or English) — match the user's last turn.
"""
    }
}
