package com.soundguard.app.alert

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.soundguard.app.ml.Detection
import com.soundguard.app.ml.SoundCategory
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * Listens for Romania's national cell-broadcast emergency alerts (RO-ALERT)
 * by intercepting notifications from known Cell Broadcast packages OR notifications
 * whose title/body contains an emergency-broadcast keyword.
 *
 * On match, emits a synthetic [Detection] with category [SoundCategory.RO_ALERT],
 * which the existing [AlertDispatcher] routes to the watch on
 * `/pulse/event/sound_alert` like any other alert.
 *
 * The user must grant Notification access (system Settings) for this to fire —
 * see [NotificationListenerHelper].
 */
@AndroidEntryPoint
class RoAlertListener : NotificationListenerService() {

    @Inject lateinit var dispatcher: AlertDispatcher

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val recentlyHandled = mutableMapOf<String, Long>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "RoAlertListener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        scope.cancel()
        Log.i(TAG, "RoAlertListener disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!isRoAlert(sbn)) return

        // Cell Broadcast notifications may re-post; dedupe by key for 60s.
        val now = System.currentTimeMillis()
        val previous = recentlyHandled[sbn.key] ?: 0L
        if (now - previous < DEDUPE_MS) return
        recentlyHandled[sbn.key] = now

        val title = sbn.notification.extras
            .getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        Log.i(TAG, "RO-ALERT detected from pkg=${sbn.packageName} title='$title'")

        scope.launch {
            dispatcher.dispatch(
                Detection(
                    category = SoundCategory.RO_ALERT,
                    score = 1.0f,
                    timestampMs = now
                )
            )
        }
    }

    private fun isRoAlert(sbn: StatusBarNotification): Boolean {
        val pkg = sbn.packageName ?: return false
        if (pkg in CELL_BROADCAST_PACKAGES) return true

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val combined = "$title $text $bigText".uppercase(Locale.ROOT)
        return RO_ALERT_KEYWORDS.any { combined.contains(it) }
    }

    private companion object {
        const val TAG = "SoundGuard.RoAlert"
        const val DEDUPE_MS = 60_000L

        // Known cell-broadcast packages across vendors.
        val CELL_BROADCAST_PACKAGES = setOf(
            "com.android.cellbroadcastreceiver",
            "com.google.android.cellbroadcastreceiver",
            "com.android.cellbroadcastservice",
            "com.google.android.cellbroadcastservice"
        )

        val RO_ALERT_KEYWORDS = setOf(
            "RO-ALERT",
            "ROALERT",
            "RO ALERT",
            "ALERTĂ",
            "EMERGENCY ALERT",
            "PRESIDENTIAL ALERT"
        )
    }
}
