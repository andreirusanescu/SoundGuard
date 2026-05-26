package com.soundguard.app.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide Text-to-Speech wrapper used by the AI Coach for hands-free
 * accessibility playback. Lazily initialised on first speak() call so we don't
 * pay the engine startup cost for users who never enable voice replies.
 */
@Singleton
class AssistantSpeaker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private val utteranceCounter = AtomicLong(0L)

    private val _speakingId = MutableStateFlow<String?>(null)
    /** Non-null while a specific utterance is playing — UIs use it to highlight the active bubble. */
    val speakingId: StateFlow<String?> = _speakingId.asStateFlow()

    private fun engine(onReady: () -> Unit) {
        if (ready && tts != null) {
            onReady()
            return
        }
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (!ready) {
                Log.w(TAG, "TextToSpeech init failed status=$status")
                return@TextToSpeech
            }
            tts?.setOnUtteranceProgressListener(progressListener)
            // Try Romanian first (the user is RO), fall back to system default.
            val ro = Locale("ro", "RO")
            val res = tts?.setLanguage(ro)
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.language = Locale.getDefault()
            }
            onReady()
        }
    }

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            _speakingId.value = utteranceId
        }

        override fun onDone(utteranceId: String?) {
            if (_speakingId.value == utteranceId) _speakingId.value = null
        }

        @Deprecated("Deprecated in API 21", ReplaceWith("onError(utteranceId, errorCode)"))
        override fun onError(utteranceId: String?) {
            if (_speakingId.value == utteranceId) _speakingId.value = null
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            Log.w(TAG, "tts error utt=$utteranceId code=$errorCode")
            if (_speakingId.value == utteranceId) _speakingId.value = null
        }
    }

    /**
     * Speak [text] aloud, stopping any in-flight utterance first. The caller
     * may pass an [id] (e.g. coach-message-id) so the UI can highlight the
     * currently-playing bubble; when null, an auto id is generated.
     */
    fun speak(text: String, id: String? = null) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val utteranceId = id ?: "tts-${utteranceCounter.incrementAndGet()}"
        engine {
            tts?.stop()
            val res = tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, Bundle(), utteranceId)
            if (res != TextToSpeech.SUCCESS) {
                Log.w(TAG, "tts.speak() returned $res")
                _speakingId.value = null
            }
        }
    }

    fun stop() {
        tts?.stop()
        _speakingId.value = null
    }

    fun shutdown() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        ready = false
        _speakingId.value = null
    }

    private companion object {
        const val TAG = "SoundGuard.TTS"
    }
}
