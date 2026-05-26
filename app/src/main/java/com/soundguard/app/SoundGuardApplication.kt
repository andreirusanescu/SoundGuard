package com.soundguard.app

import android.app.Application
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import com.soundguard.app.alert.AlertDispatcher
import com.soundguard.app.data.DetectionRepository
import com.soundguard.app.ml.SoundClassifier
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SoundGuardApplication : Application() {

    @Inject lateinit var classifier: SoundClassifier
    @Inject lateinit var dispatcher: AlertDispatcher
    @Inject lateinit var repository: DetectionRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Application.onCreate; classifier=$classifier dispatcher=$dispatcher")

        // Bridge: every classifier emission becomes a dispatch attempt.
        appScope.launch {
            Log.i(TAG, "bridge coroutine started")
            classifier.detections.collect { detection ->
                try {
                    dispatcher.dispatch(detection)
                } catch (e: Exception) {
                    Log.e(TAG, "dispatch threw", e)
                }
            }
        }

        // Heartbeat + stale prune.
        appScope.launch {
            var tick = 0
            while (isActive) {
                delay(STALE_CHECK_INTERVAL_MS)
                repository.pruneStale()
                if (++tick % 5 == 0) {
                    Log.v(TAG, "heartbeat tick=$tick listening=${repository.isListening.value}")
                }
            }
        }

        // Diagnostic: log connected wear nodes every 10s. If this stays at 0,
        // the watch app's applicationId does NOT match com.soundguard.app
        // (Wearable Data Layer pairs apps by package name, not just devices).
        appScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val nodes = Tasks.await(Wearable.getNodeClient(this@SoundGuardApplication).connectedNodes)
                    Log.i(
                        TAG,
                        "connected wear nodes: ${nodes.size} ${
                            nodes.map { "${it.displayName}(${it.id})" }
                        }"
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "node query failed: ${e.message}")
                }
                delay(NODE_PROBE_INTERVAL_MS)
            }
        }
    }

    private companion object {
        const val TAG = "SoundGuard.App"
        const val STALE_CHECK_INTERVAL_MS = 1_000L
        const val NODE_PROBE_INTERVAL_MS = 10_000L
    }
}
