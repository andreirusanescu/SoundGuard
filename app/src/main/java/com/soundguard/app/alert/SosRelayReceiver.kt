package com.soundguard.app.alert

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.soundguard.app.mail.GmailSender
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

/**
 * Inbound channel for the watch's SOS escalation. The watch never holds any
 * email credential — when an alert escalates, it sends the trusted-contacts
 * list and the message body here over the Wearable Data Layer, and the
 * phone calls the Gmail REST API with the user's OAuth access token.
 *
 *  Path: /pulse/sos/send
 *  Body: UTF-8 JSON
 *    {
 *      "to":      ["a@b.com", ...],   // mandatory, non-empty
 *      "subject": "...",
 *      "body":    "..."
 *    }
 */
@AndroidEntryPoint
class SosRelayReceiver : WearableListenerService() {

    @Inject lateinit var gmail: GmailSender

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != PATH) return
        val payload = runCatching { JSONObject(String(messageEvent.data, Charsets.UTF_8)) }
            .getOrNull()
        if (payload == null) {
            Log.w(TAG, "malformed SOS payload — dropping")
            return
        }
        val toArray = payload.optJSONArray("to") ?: return
        val recipients = (0 until toArray.length()).map { toArray.getString(it) }
        val subject = payload.optString("subject", "🚨 SoundGuard SOS")
        val body = payload.optString("body", "")
        if (recipients.isEmpty()) {
            Log.w(TAG, "SOS payload had empty recipient list")
            return
        }
        Log.i(TAG, "relaying SOS to ${recipients.size} recipient(s)")
        scope.launch {
            runCatching { gmail.send(recipients, subject, body) }
                .onFailure { Log.e(TAG, "gmail.send threw", it) }
        }
    }

    companion object {
        private const val TAG = "SosRelayRx"
        const val PATH = "/pulse/sos/send"
    }
}
