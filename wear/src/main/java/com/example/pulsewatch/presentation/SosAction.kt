package com.example.pulsewatch.presentation

import android.content.Context
import android.location.Location
import android.util.Log
import com.example.pulsewatch.data.TrustedContactsStore
import com.example.pulsewatch.safety.lastKnownLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val sosScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * SOS flow (zero-secret email):
 *  1) Read the watch's last-known GPS fix.
 *  2) Compose a subject + body that includes the alert label and a Google
 *     Maps link to the coordinates.
 *  3) Hand the payload to [EmailSender], which forwards it to the paired
 *     phone over the Wearable Data Layer. The phone uses an OAuth
 *     `gmail.send` access token (granted once by the user in Settings) and
 *     calls Gmail REST API. No SMTP password lives in either APK.
 *
 * @param alertLabel Sound type (e.g. "Siren"). Null when the user tapped the
 *   manual Quick SOS on the home screen with no active alert.
 * @param auto True when the alert screen escalated automatically because the
 *   user did not respond inside [com.example.pulsewatch.data.SOS_ESCALATION_SECONDS].
 */
internal fun triggerSos(
    context: Context,
    alertLabel: String? = null,
    auto: Boolean = false,
    biometricSpike: Boolean = false,
) {
    val appCtx = context.applicationContext
    sosScope.launch {
        val recipients = TrustedContactsStore.snapshot(appCtx)
        if (recipients.isEmpty()) {
            Log.w("SosAction", "no trusted contacts configured — SOS NOT sent")
            return@launch
        }
        val location = lastKnownLocation(appCtx)
        val (subject, body) = buildSosMessage(alertLabel, location, auto, biometricSpike)
        EmailSender.send(appCtx, recipients, subject, body)
    }
}

private fun buildSosMessage(
    alertLabel: String?,
    location: Location?,
    auto: Boolean,
    biometricSpike: Boolean,
): Pair<String, String> {
    val subject = buildString {
        append("🚨 SoundGuard ")
        append(
            when {
                biometricSpike -> "CRITICAL"
                auto -> "AUTO-ESCALATE"
                else -> "SOS"
            }
        )
        if (alertLabel != null) append(": $alertLabel detected")
        if (biometricSpike) append(" + low SpO2")
    }

    val coordinates = location?.let {
        "%.6f, %.6f".format(Locale.US, it.latitude, it.longitude)
    } ?: "unknown"
    val accuracy = location?.accuracy?.let { "±${it.toInt()} m" } ?: "n/a"
    val mapsLink = location?.let {
        "https://maps.google.com/?q=${"%.6f".format(Locale.US, it.latitude)}," +
            "%.6f".format(Locale.US, it.longitude)
    } ?: "(GPS fix unavailable)"
    val ageSec = location?.let {
        ((System.currentTimeMillis() - it.time) / 1000).coerceAtLeast(0)
    }
    val freshness = ageSec?.let { "fix age: ${it}s" } ?: "no fix"
    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date())

    val body = buildString {
        when {
            biometricSpike -> appendLine(
                "SoundGuard detected a CRITICAL hazard near your contact AND " +
                    "their wrist SpO2 dropped sharply at the same moment. The " +
                    "watch escalated immediately without waiting for confirmation."
            )
            auto -> appendLine(
                "SoundGuard detected a hazard near your contact and they did not " +
                    "respond on the watch. This is an automatic escalation."
            )
            alertLabel != null -> appendLine(
                "Your contact tapped SOS after SoundGuard flagged a hazard."
            )
            else -> appendLine(
                "Your contact tapped Quick SOS on their watch."
            )
        }
        appendLine()
        if (alertLabel != null) appendLine("Detected sound: $alertLabel")
        if (biometricSpike) appendLine("Biometric: wrist SpO2 spike (≥4% drop or <92%)")
        appendLine("Time: $timestamp")
        appendLine()
        appendLine("Coordinates: $coordinates  ($accuracy, $freshness)")
        appendLine("Map: $mapsLink")
        appendLine()
        append("— Sent automatically by SoundGuard on Wear OS.")
    }

    return subject to body
}

