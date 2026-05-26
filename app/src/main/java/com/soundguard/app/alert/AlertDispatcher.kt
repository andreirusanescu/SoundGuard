package com.soundguard.app.alert

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import com.soundguard.app.data.DetectionRepository
import com.soundguard.app.data.db.AlertEventDao
import com.soundguard.app.data.db.AlertEventEntity
import com.soundguard.app.ml.Detection
import com.soundguard.app.ml.SoundCategory
import com.soundguard.app.network.AlertApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Forwards detections to the watch over the Wearable Data Layer and persists them in Room.
 *
 * Protocol (`SoundGuard — Watch ↔ Phone Protocol`):
 *   path:    /pulse/event/sound_alert
 *   body:    UTF-8 string `TYPE|DIRECTION|CONFIDENCE|TIMESTAMP_MS`
 *   debounce: at most one alert per TYPE every 5 seconds (watch does no dedupe).
 */
@Singleton
class AlertDispatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DetectionRepository,
    private val alertDao: AlertEventDao
) {
    private val lastDispatchByCategory = mutableMapOf<SoundCategory, Long>()
    // Detached so the HTTP roundtrip can't slow the watch path. Failures are
    // logged inside AlertApi; nothing here depends on the response.
    private val networkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun dispatch(detection: Detection) {
        Log.d(TAG, "dispatch: ${detection.category.name} ${"%.3f".format(detection.score)}")
        repository.emit(detection)
        val previous = lastDispatchByCategory[detection.category] ?: 0L
        if (detection.timestampMs - previous < DEDUPE_WINDOW_MS) {
            Log.d(TAG, "debounced: ${detection.category.name}")
            return
        }
        lastDispatchByCategory[detection.category] = detection.timestampMs
        persist(detection)
        sendToWear(detection)
        reportToServer(detection)
    }

    private fun reportToServer(detection: Detection) {
        networkScope.launch {
            runCatching {
                AlertApi.reportAlert(
                    context = context,
                    alertType = detection.category.name,
                    severity = detection.category.severity.name,
                )
            }.onFailure { Log.w(TAG, "reportToServer failed: ${it.message}") }
        }
    }

    private suspend fun persist(detection: Detection) = withContext(Dispatchers.IO) {
        runCatching {
            alertDao.insert(
                AlertEventEntity(
                    category = detection.category.name,
                    score = detection.score,
                    timestampMs = detection.timestampMs
                )
            )
        }.onFailure { Log.w(TAG, "persist failed: ${it.message}") }
    }

    private suspend fun sendToWear(detection: Detection) = withContext(Dispatchers.IO) {
        try {
            val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes)
            Log.d(TAG, "connected nodes: ${nodes.size}")
            if (nodes.isEmpty()) {
                Log.w(TAG, "no paired watch — alert NOT sent")
                return@withContext
            }
            val payload = buildString {
                append(detection.category.name)
                append('|')
                append(DIRECTION_CENTER)
                append('|')
                append(detection.score)
                append('|')
                append(detection.timestampMs)
            }.toByteArray(Charsets.UTF_8)
            val messageClient = Wearable.getMessageClient(context)
            for (node in nodes) {
                runCatching {
                    Tasks.await(messageClient.sendMessage(node.id, MESSAGE_PATH, payload))
                    Log.i(TAG, "alert sent to ${node.id}: ${detection.category.name}")
                }.onFailure { e ->
                    Log.w(TAG, "sendMessage to ${node.id} failed: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendToWear failed", e)
        }
    }

    private companion object {
        const val TAG = "SoundGuard.Disp"
        const val MESSAGE_PATH = "/pulse/event/sound_alert"
        const val DIRECTION_CENTER = "CENTER"
        const val DEDUPE_WINDOW_MS = 5_000L
    }
}
