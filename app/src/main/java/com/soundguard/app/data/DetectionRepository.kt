package com.soundguard.app.data

import com.soundguard.app.ml.Detection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DetectionRepository @Inject constructor() {
    private val _last = MutableStateFlow<Detection?>(null)
    val last: StateFlow<Detection?> = _last.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    @Volatile
    private var lastChunkAtMs: Long = 0L

    fun emit(detection: Detection) {
        _last.value = detection
    }

    /** Called by [com.soundguard.app.audio.AudioChunkReceiver] on every received chunk. */
    fun markChunkReceived() {
        lastChunkAtMs = System.currentTimeMillis()
        if (!_isListening.value) _isListening.value = true
    }

    /** Periodic check — flips isListening false if no chunks for [staleMs]. */
    fun pruneStale(staleMs: Long = STALE_THRESHOLD_MS) {
        if (_isListening.value && System.currentTimeMillis() - lastChunkAtMs > staleMs) {
            _isListening.value = false
        }
    }

    private companion object {
        const val STALE_THRESHOLD_MS = 3_000L
    }
}
