package com.example.pulsewatch.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.example.pulsewatch.R
import com.example.pulsewatch.presentation.MainActivity
import com.example.pulsewatch.safety.health.BiometricMonitor
import com.example.pulsewatch.safety.sound.AudioCapture
import com.example.pulsewatch.safety.sound.AudioChunkSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that streams the watch microphone to the paired phone.
 *
 * Pipeline:
 *   AudioCapture (16 kHz mono PCM_16BIT, 500ms chunks)
 *     → AudioChunkSender → MessageClient → /pulse/audio/stream/chunk → phone
 *
 * The phone runs the YAMNet classifier and pushes alerts back via /pulse/event/sound_alert,
 * which SoundAlertReceiver handles (vibration + SoundAlertScreen).
 */
class SoundGuardService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var streamJob: Job? = null
    private val capture = AudioCapture()
    private lateinit var sender: AudioChunkSender
    private var wakeLock: PowerManager.WakeLock? = null

    private var chunksSent = 0L

    override fun onCreate() {
        super.onCreate()
        sender = AudioChunkSender(this)
        sender.resolveNode()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification("Streaming audio to phone")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
        acquireWakeLock()
        BiometricMonitor.start(this)
        isRunning = true
        startStreaming()
        return START_STICKY
    }

    override fun onDestroy() {
        streamJob?.cancel()
        scope.cancel()
        BiometricMonitor.stop(this)
        releaseWakeLock()
        isRunning = false
        super.onDestroy()
        Log.d(TAG, "SoundGuardService destroyed (chunks sent=$chunksSent)")
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java) ?: return
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SoundGuard:listenerWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startStreaming() {
        if (streamJob?.isActive == true) return
        streamJob = scope.launch {
            try {
                capture.start().collect { samples ->
                    sender.send(samples)
                    chunksSent++
                    if (chunksSent % 20 == 0L) {
                        Log.d(TAG, "Streamed $chunksSent chunks (${chunksSent / 2}s of audio)")
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Audio capture/stream failed", e)
            }
        }
    }

    // ─── Notifications ───────────────────────────────────────────────────

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "SoundGuard listener",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Streams microphone audio to the paired phone for sound classification"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("SoundGuard active")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val TAG = "SoundGuardService"
        private const val CHANNEL_ID = "soundguard_listener"
        private const val NOTIF_ID = 4711

        // Read by MainActivity on resume to restore the toggle UI state when
        // the user re-enters the app while the foreground service is still alive.
        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, SoundGuardService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SoundGuardService::class.java))
        }
    }
}
