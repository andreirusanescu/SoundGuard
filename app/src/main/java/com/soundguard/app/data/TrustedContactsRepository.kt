package com.soundguard.app.data

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps [PreferencesRepository] for the trusted-contacts list and pushes
 * every change to the watch over the Wearable Data Layer.
 *
 *  Path:    /pulse/contacts/sync
 *  Body:    UTF-8, one email per line. Empty body == cleared list.
 *
 * The watch persists what it receives in its own SharedPreferences-backed
 * store so it can survive an offline boot.
 */
@Singleton
class TrustedContactsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PreferencesRepository,
) {
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Observed by the UI. Sorted, deduped, lower-cased. */
    val contacts: Flow<List<String>> = prefs.trustedContacts
        .distinctUntilChanged()
        .onEach { latest -> syncToWear(latest) }

    suspend fun add(email: String) = prefs.addTrustedContact(email)
    suspend fun remove(email: String) = prefs.removeTrustedContact(email)

    private fun syncToWear(emails: List<String>) {
        syncScope.launch {
            runCatching {
                val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes)
                if (nodes.isEmpty()) {
                    Log.d(TAG, "no connected watch — skipping contacts sync")
                    return@launch
                }
                val payload = emails.joinToString("\n").toByteArray(Charsets.UTF_8)
                val client = Wearable.getMessageClient(context)
                for (node in nodes) {
                    runCatching {
                        Tasks.await(client.sendMessage(node.id, MESSAGE_PATH, payload))
                        Log.i(TAG, "contacts pushed to ${node.id}: ${emails.size} entries")
                    }.onFailure { Log.w(TAG, "sendMessage failed on ${node.id}: ${it.message}") }
                }
            }.onFailure { Log.w(TAG, "syncToWear failed: ${it.message}") }
        }
    }

    private companion object {
        const val TAG = "SoundGuard.Contacts"
        const val MESSAGE_PATH = "/pulse/contacts/sync"
    }
}
