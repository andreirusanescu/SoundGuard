package com.example.pulsewatch.safety.sound

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

class AudioCapture(
    val sampleRate: Int = 16_000,
    private val chunkMs: Int = 500
) {
    private val chunkSize: Int = sampleRate * chunkMs / 1000

    @SuppressLint("MissingPermission") // caller is responsible for RECORD_AUDIO
    fun start(): Flow<ShortArray> = flow {
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            Log.e(TAG, "AudioRecord min buffer invalid: $minBuffer")
            return@flow
        }
        val bufferSize = maxOf(minBuffer * 4, sampleRate * 2)

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            record.release()
            return@flow
        }

        record.startRecording()
        Log.d(TAG, "AudioCapture started: $sampleRate Hz, chunk=$chunkSize samples (${chunkMs}ms)")

        val buffer = ShortArray(chunkSize)
        try {
            while (currentCoroutineContext().isActive) {
                var offset = 0
                // Fill the chunk completely before emitting
                while (offset < chunkSize && currentCoroutineContext().isActive) {
                    val read = record.read(buffer, offset, chunkSize - offset)
                    if (read <= 0) {
                        Log.w(TAG, "AudioRecord.read returned $read")
                        break
                    }
                    offset += read
                }
                if (offset == chunkSize) {
                    emit(buffer.copyOf())
                }
            }
        } finally {
            try {
                record.stop()
            } catch (_: IllegalStateException) {}
            record.release()
            Log.d(TAG, "AudioCapture stopped")
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        private const val TAG = "AudioCapture"
    }
}
