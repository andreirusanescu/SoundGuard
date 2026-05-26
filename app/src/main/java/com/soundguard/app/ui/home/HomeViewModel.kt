package com.soundguard.app.ui.home

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.soundguard.app.ai.CoachRepository
import com.soundguard.app.alert.AlertDispatcher
import com.soundguard.app.alert.NotificationListenerHelper
import com.soundguard.app.data.DetectionRepository
import com.soundguard.app.ml.Detection
import com.soundguard.app.ml.Severity
import com.soundguard.app.ml.SoundCategory
import com.soundguard.app.network.AlertApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val EMERGENCY_WINDOW_MS = 5 * 60_000L

sealed class LocationCheckState {
    object Idle : LocationCheckState()
    object Checking : LocationCheckState()
    data class Done(val isDangerous: Boolean, val nearbyAlertsCount: Int) : LocationCheckState()
    data class Error(val message: String) : LocationCheckState()
}

data class HomeUiState(
    val isListening: Boolean = false,
    val lastDetection: Detection? = null,
    val isRoAlertListenerEnabled: Boolean = false,
    val emergencyDetection: Detection? = null,
    val locationCheck: LocationCheckState = LocationCheckState.Idle,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    application: Application,
    repository: DetectionRepository,
    private val dispatcher: AlertDispatcher,
    private val coachRepository: CoachRepository
) : AndroidViewModel(application) {

    private val context get() = getApplication<Application>()

    private val _listenerEnabled = MutableStateFlow(NotificationListenerHelper.isEnabled(context))

    init {
        viewModelScope.launch {
            while (isActive) {
                _listenerEnabled.value = NotificationListenerHelper.isEnabled(context)
                delay(POLL_LISTENER_MS)
            }
        }
    }

    // 30-second tick keeps the emergency-window evaluation fresh even when no
    // new detection arrives (so the banner auto-dismisses after 5 min).
    private val tick: StateFlow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(EMERGENCY_TICK_MS)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    private val _locationCheck = MutableStateFlow<LocationCheckState>(LocationCheckState.Idle)

    val uiState: StateFlow<HomeUiState> = combine(
        repository.isListening,
        repository.last,
        _listenerEnabled,
        tick,
        _locationCheck,
    ) { listening, last, listenerEnabled, now, locationCheck ->
        val emergency = last?.takeIf {
            it.category.severity == Severity.CRITICAL &&
                (now - it.timestampMs) < EMERGENCY_WINDOW_MS
        }
        HomeUiState(listening, last, listenerEnabled, emergency, locationCheck)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun checkLocationDanger() {
        if (_locationCheck.value is LocationCheckState.Checking) return
        _locationCheck.value = LocationCheckState.Checking
        viewModelScope.launch {
            _locationCheck.value = when (val r = AlertApi.checkLocation(context)) {
                is AlertApi.CheckResult.Done -> LocationCheckState.Done(r.isDangerous, r.nearbyAlertsCount)
                is AlertApi.CheckResult.Error -> LocationCheckState.Error(r.message)
                AlertApi.CheckResult.NoLocation -> LocationCheckState.Error("No GPS fix yet")
            }
        }
    }

    fun sendTestAlert(category: SoundCategory) {
        viewModelScope.launch {
            dispatcher.dispatch(
                Detection(category, TEST_CONFIDENCE, System.currentTimeMillis())
            )
        }
    }

    fun sendTestRoAlert() = sendTestAlert(SoundCategory.RO_ALERT)

    fun openListenerSettings() = NotificationListenerHelper.openSettings(context)

    /**
     * Sends a context-rich prompt to the Coach (which will also auto-switch tabs).
     * Called when the user taps "Ask AI: what should I do?" on the emergency card.
     */
    fun askCoachAboutEmergency(detection: Detection) {
        val mins = ((System.currentTimeMillis() - detection.timestampMs) / 60_000L).coerceAtLeast(0L)
        val ago = if (mins == 0L) "just now" else "$mins min ago"
        val prompt = "I just got a ${detection.category.displayName} alert ($ago). " +
            "Walk me through what to do right now in 4 short steps."
        viewModelScope.launch {
            coachRepository.submitIntent(prompt, origin = "home_emergency")
        }
    }

    fun call112() {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun shareSafetyMessage(detection: Detection) {
        val text = "[SoundGuard] I just received a ${detection.category.displayName} alert " +
            "near me. If you don't hear from me in a few minutes, please check in."
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "Share safety check-in").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(chooser) }
    }

    private companion object {
        const val TEST_CONFIDENCE = 0.95f
        const val POLL_LISTENER_MS = 2_000L
        const val EMERGENCY_TICK_MS = 30_000L
    }
}
