package com.example.pulsewatch.service

import android.content.Intent
import android.util.Log
import com.example.pulsewatch.data.SOUND_ALERT_PATH
import com.example.pulsewatch.data.SoundAlertPayload
import com.example.pulsewatch.presentation.MainActivity
import com.example.pulsewatch.safety.HapticController
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class SoundAlertReceiver : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != SOUND_ALERT_PATH) return

        val payload = SoundAlertPayload.fromBytes(messageEvent.data)
        if (payload == null) {
            Log.w(TAG, "Received malformed sound alert payload")
            return
        }

        Log.d(TAG, "Sound alert received: ${payload.type} from ${payload.direction} (${payload.confidence})")

        // Trigger haptic immediately — no UI dependency
        HapticController(this).vibrateForAlert(payload)

        // Bring MainActivity to the foreground with the alert
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_ALERT_BYTES, payload.toBytes())
        }
        startActivity(intent)
    }

    companion object {
        private const val TAG = "SoundAlertReceiver"
        const val EXTRA_ALERT_BYTES = "sound_alert_bytes"
    }
}
