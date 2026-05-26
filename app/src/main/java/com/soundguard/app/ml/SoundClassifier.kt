package com.soundguard.app.ml

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifier
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifierResult
import com.google.mediapipe.tasks.audio.core.RunningMode
import com.google.mediapipe.tasks.components.containers.AudioData
import com.google.mediapipe.tasks.core.BaseOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundClassifier @Inject constructor(
    @ApplicationContext context: Context
) {
    private val _detections = MutableSharedFlow<Detection>(
        replay = 0,
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val detections: SharedFlow<Detection> = _detections.asSharedFlow()

    private val classifier: AudioClassifier = run {
        Log.i(TAG, "Initialising AudioClassifier (model=$MODEL_FILE)")
        AudioClassifier.createFromOptions(
            context,
            AudioClassifier.AudioClassifierOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath(MODEL_FILE)
                        .build()
                )
                .setRunningMode(RunningMode.AUDIO_STREAM)
                .setMaxResults(MAX_RESULTS)
                .setScoreThreshold(BASE_SCORE_THRESHOLD)
                .setResultListener(::onResult)
                .setErrorListener { e -> Log.e(TAG, "Classifier error", e) }
                .build()
        ).also { Log.i(TAG, "AudioClassifier ready") }
    }

    private var feedCount: Long = 0
    private var resultCount: Long = 0

    fun feed(buffer: FloatArray, samplesRead: Int, sampleRate: Int, timestampMs: Long) {
        if (samplesRead <= 0) return
        feedCount++
        if (feedCount % 20L == 1L) {
            Log.d(TAG, "feed #$feedCount: $samplesRead samples @ ${sampleRate}Hz, ts=$timestampMs")
        }
        try {
            val format = AudioData.AudioDataFormat.builder()
                .setNumOfChannels(1)
                .setSampleRate(sampleRate.toFloat())
                .build()
            val data = AudioData.create(format, samplesRead)
            if (samplesRead == buffer.size) {
                data.load(buffer)
            } else {
                data.load(buffer.copyOf(samplesRead))
            }
            classifier.classifyAsync(data, timestampMs)
        } catch (e: Exception) {
            Log.e(TAG, "feed failed", e)
        }
    }

    fun close() = classifier.close()

    private fun onResult(result: AudioClassifierResult) {
        val ts = System.currentTimeMillis()
        resultCount++
        var matched = false
        result.classificationResults().forEach { cr ->
            cr.classifications().forEach { cls ->
                cls.categories().forEach { cat ->
                    val name = cat.categoryName()
                    val score = cat.score()
                    if (score >= LOG_VISIBILITY_THRESHOLD) {
                        Log.d(TAG, "YAMNet '${name}' = ${"%.3f".format(score)}")
                    }
                    val mapped = SoundCategory.fromYamnetLabel(name) ?: return@forEach
                    if (score >= mapped.threshold) {
                        Log.i(TAG, "DETECTED ${mapped.name} (raw='$name', score=${"%.3f".format(score)})")
                        _detections.tryEmit(Detection(mapped, score, ts))
                        matched = true
                    }
                }
            }
        }
        if (resultCount % 10L == 1L && !matched) {
            Log.v(TAG, "result #$resultCount: no whitelist match")
        }
    }

    private companion object {
        const val TAG = "SoundGuard.Clf"
        const val MODEL_FILE = "yamnet.tflite"
        const val MAX_RESULTS = 10
        // Low base threshold so logs surface what YAMNet is actually hearing.
        // Real detection still gates on per-category thresholds in SoundCategory.
        const val BASE_SCORE_THRESHOLD = 0.05f
        const val LOG_VISIBILITY_THRESHOLD = 0.20f
    }
}
