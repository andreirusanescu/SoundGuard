package com.soundguard.app.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soundguard.app.ai.CoachRepository
import com.soundguard.app.data.DailyBriefingCache
import com.soundguard.app.data.HealthRepository
import com.soundguard.app.data.HealthSummary
import com.soundguard.app.data.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class DailyBriefingState(
    val text: String = "",
    val generatedAtMs: Long? = null,
    val isLoading: Boolean = false
)

@HiltViewModel
class HealthViewModel @Inject constructor(
    health: HealthRepository,
    private val coach: CoachRepository,
    private val prefs: PreferencesRepository
) : ViewModel() {

    val summary: StateFlow<HealthSummary> = health.summary
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000),
            HealthSummary(0, 0, null, null, List(7) { 0 }))

    private val _aiInsight = MutableStateFlow("")
    private val _insightLoading = MutableStateFlow(false)
    val aiInsight: StateFlow<String> = _aiInsight.asStateFlow()
    val isInsightLoading: StateFlow<Boolean> = _insightLoading.asStateFlow()

    private val _briefing = MutableStateFlow(DailyBriefingState())
    val briefing: StateFlow<DailyBriefingState> = _briefing.asStateFlow()

    val isCoachConfigured: Boolean get() = coach.isConfigured

    init {
        viewModelScope.launch {
            val cached = prefs.briefing.firstOrNull()
            _briefing.value = DailyBriefingState(
                text = cached?.text.orEmpty(),
                generatedAtMs = cached?.generatedAtMs
            )
            // Auto-refresh once per local calendar day if the coach is configured.
            if (coach.isConfigured && shouldRefreshBriefing(cached)) {
                generateBriefing()
            }
        }
    }

    fun requestInsight() {
        if (_insightLoading.value || !coach.isConfigured) return
        _aiInsight.value = ""
        _insightLoading.value = true
        viewModelScope.launch {
            val builder = StringBuilder()
            runCatching {
                coach.oneShot(
                    "Give me a short personalized hearing-care summary based on my recent alert pattern. " +
                        "End with one specific actionable tip."
                ).collect { delta ->
                    builder.append(delta)
                    _aiInsight.value = builder.toString()
                }
            }
            _insightLoading.value = false
        }
    }

    fun generateBriefing() {
        if (_briefing.value.isLoading || !coach.isConfigured) return
        _briefing.value = _briefing.value.copy(isLoading = true)
        viewModelScope.launch {
            val builder = StringBuilder()
            runCatching {
                coach.oneShot(
                    "Greet me by name (if known) for today's daily briefing. " +
                        "In ≤70 words: summarise yesterday's sound exposure, name today's biggest care priority, " +
                        "and end with one calm one-line affirmation. Use a warm tone."
                ).collect { delta ->
                    builder.append(delta)
                    _briefing.value = _briefing.value.copy(text = builder.toString())
                }
            }
            val finalText = builder.toString().trim()
            val now = System.currentTimeMillis()
            if (finalText.isNotBlank()) {
                prefs.saveBriefing(finalText, now)
            }
            _briefing.value = DailyBriefingState(
                text = finalText.ifBlank { _briefing.value.text },
                generatedAtMs = if (finalText.isNotBlank()) now else _briefing.value.generatedAtMs,
                isLoading = false
            )
        }
    }

    private fun shouldRefreshBriefing(cached: DailyBriefingCache?): Boolean {
        if (cached == null) return true
        val cal = Calendar.getInstance()
        val today = dayKey(cal)
        cal.timeInMillis = cached.generatedAtMs
        val cachedDay = dayKey(cal)
        return today != cachedDay
    }

    private fun dayKey(cal: Calendar): Int =
        cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
}
