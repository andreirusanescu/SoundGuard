package com.example.pulsewatch.presentation

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.example.pulsewatch.data.SoundAlertPayload
import com.example.pulsewatch.data.TrustedContactsStore
import com.example.pulsewatch.presentation.theme.PulseWatchTheme
import com.example.pulsewatch.safety.health.BiometricMonitor
import com.example.pulsewatch.service.SoundAlertReceiver
import com.example.pulsewatch.service.SoundGuardService
import java.util.Calendar

class MainActivity : ComponentActivity() {

    private var currentAlert by mutableStateOf<SoundAlertPayload?>(null)
    private var currentAlertFastEscalate by mutableStateOf(false)
    private var listening by mutableStateOf(false)
    private var todayAlerts by mutableIntStateOf(0)
    private var todayAlertsDay: Int = -1
    private var screen by mutableStateOf(WearScreen.HOME)

    private val voiceContact = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            .orEmpty()
        val parsed = spokenToEmail(spoken)
        if (parsed != null && TrustedContactsStore.add(this, parsed)) {
            Toast.makeText(this, "Added $parsed", Toast.LENGTH_SHORT).show()
        } else {
            Log.w("MainActivity", "voice add rejected: heard=\"$spoken\" parsed=$parsed")
            Toast.makeText(this, "Couldn't parse email", Toast.LENGTH_SHORT).show()
        }
    }

    private val audioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startListener() else listening = false
    }

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* best-effort; service runs regardless */ }

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* best-effort; SOS email still sends, just without coordinates */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TrustedContactsStore.ensureLoaded(this)
        handleIntent(intent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Location is needed so the SOS email body can include GPS coordinates
        // and a Google Maps link.
        val locationNeed = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ).filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (locationNeed.isNotEmpty()) {
            locationPermission.launch(locationNeed.toTypedArray())
        }

        setContent {
            PulseWatchTheme {
                val alert = currentAlert
                when {
                    alert != null -> {
                        val fast = currentAlertFastEscalate
                        SoundAlertScreen(
                            payload = alert,
                            fastEscalate = fast,
                            onDismiss = { currentAlert = null },
                            onTimeoutEscalate = {
                                triggerSos(
                                    context = this,
                                    alertLabel = alert.type.label,
                                    auto = true,
                                    biometricSpike = fast,
                                )
                                currentAlert = null
                            }
                        )
                    }
                    screen == WearScreen.CONTACTS -> TrustedContactsScreen(
                        onBack = { screen = WearScreen.HOME },
                        onAddViaVoice = { launchVoiceContact() },
                    )
                    else -> WearHomeScreen(
                        listening = listening,
                        todayAlerts = todayAlerts,
                        ambientDb = null,
                        onToggleListener = { toggleListener() },
                        onOpenContacts = { screen = WearScreen.CONTACTS },
                    )
                }
            }
        }
    }

    private fun launchVoiceContact() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say the email address")
        }
        runCatching { voiceContact.launch(intent) }
            .onFailure {
                Toast.makeText(this, "Voice not available", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Service may be alive even though the Activity was destroyed —
        // re-sync the toggle UI with the actual foreground service state.
        listening = SoundGuardService.isRunning
    }

    private fun handleIntent(intent: Intent?) {
        val bytes = intent?.getByteArrayExtra(SoundAlertReceiver.EXTRA_ALERT_BYTES) ?: return
        val payload = SoundAlertPayload.fromBytes(bytes) ?: return
        // Compute the biometric decision once at intent time so the demo flag
        // (consumed inside shouldFastEscalate) only fires for THIS alert.
        currentAlertFastEscalate = BiometricMonitor.shouldFastEscalate(payload.type)
        currentAlert = payload
        bumpTodayCount()
    }

    private fun bumpTodayCount() {
        val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        if (today != todayAlertsDay) {
            todayAlertsDay = today
            todayAlerts = 0
        }
        todayAlerts += 1
    }

    private fun toggleListener() {
        if (listening) {
            SoundGuardService.stop(this)
            listening = false
            return
        }
        // Need RECORD_AUDIO before starting
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startListener()
        } else {
            audioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startListener() {
        SoundGuardService.start(this)
        listening = true
    }
}

private enum class WearScreen { HOME, CONTACTS }

private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

/**
 * Best-effort speech-to-email parser. Spoken phrases like
 *   "alex at gmail dot com"   -> "alex@gmail.com"
 *   "alex@gmail.com"          -> "alex@gmail.com" (Pixel ASR sometimes returns this)
 * Returns null when the cleaned string fails [EMAIL_REGEX].
 */
internal fun spokenToEmail(raw: String): String? {
    if (raw.isBlank()) return null
    val cleaned = raw.lowercase()
        .replace(Regex("\\s+at\\s+"), "@")
        .replace(Regex("\\s+dot\\s+"), ".")
        .replace(Regex("\\s+underscore\\s+"), "_")
        .replace(Regex("\\s+dash\\s+"), "-")
        .replace(Regex("\\s+plus\\s+"), "+")
        .replace(" ", "")
    return cleaned.takeIf { EMAIL_REGEX.matches(it) }
}
