package com.example.pulsewatch.service

import android.util.Log
import com.example.pulsewatch.data.TrustedContactsStore
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/**
 * Inbound channel for trusted-contact list updates from the phone.
 *
 *  Path: /pulse/contacts/sync
 *  Body: UTF-8 string, one email per line. Empty body => cleared list.
 */
class TrustedContactsReceiver : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != PATH) return
        val raw = String(messageEvent.data, Charsets.UTF_8)
        val emails = raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        Log.i(TAG, "trusted contacts sync: ${emails.size} entries")
        TrustedContactsStore.replace(this, emails)
    }

    companion object {
        private const val TAG = "TrustedContactsRx"
        const val PATH = "/pulse/contacts/sync"
    }
}
