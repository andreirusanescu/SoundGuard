package com.soundguard.app.audio

import android.util.Log
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.soundguard.app.data.DetectionRepository
import com.soundguard.app.ml.SoundClassifier
import dagger.hilt.android.AndroidEntryPoint
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject

/**
 * Receives PCM audio chunks from the watch and surfaces every other Wearable
 * Data Layer event we can see, so Logcat shows whichever transport the
 * watch app actually uses (MessageClient / DataClient / ChannelClient).
 *
 * Once we observe the real transport, we narrow this down to just the
 * chunk path on the matching callback.
 */
@AndroidEntryPoint
class AudioChunkReceiver : WearableListenerService() {

    @Inject lateinit var classifier: SoundClassifier
    @Inject lateinit var repository: DetectionRepository

    private var chunkCount: Long = 0

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "AudioChunkReceiver created; classifier=$classifier")
    }

    // MessageClient.sendMessage(...)
    override fun onMessageReceived(event: MessageEvent) {
        val path = event.path
        val data = event.data
        Log.i(TAG, "MESSAGE path='$path' from=${event.sourceNodeId} size=${data?.size}")
        if (path != PATH_CHUNK) return
        if (data == null || data.size < 2) return
        chunkCount++
        if (chunkCount == 1L || chunkCount % 20L == 0L) {
            Log.i(TAG, "chunk #$chunkCount: ${data.size} bytes")
        }
        try {
            val shorts = decodePcm16Le(data)
            val floats = FloatArray(shorts.size) { shorts[it] / 32768f }
            classifier.feed(floats, floats.size, SAMPLE_RATE, System.currentTimeMillis())
            repository.markChunkReceived()
        } catch (e: Exception) {
            Log.e(TAG, "chunk decode failed", e)
        }
    }

    // DataClient.putDataItem(...)
    override fun onDataChanged(events: DataEventBuffer) {
        Log.i(TAG, "DATA_CHANGED count=${events.count}")
        events.forEach { ev ->
            Log.i(
                TAG,
                "  data type=${ev.type} path=${ev.dataItem.uri.path} dataSize=${ev.dataItem.data?.size}"
            )
        }
    }

    // ChannelClient.openChannel(...)
    override fun onChannelOpened(channel: ChannelClient.Channel) {
        Log.i(TAG, "CHANNEL_OPENED path='${channel.path}' from=${channel.nodeId}")
    }

    // CapabilityClient.addLocalCapability(...) on either side
    override fun onCapabilityChanged(info: CapabilityInfo) {
        Log.i(TAG, "CAPABILITY_CHANGED name='${info.name}' nodes=${info.nodes.size}")
    }

    private fun decodePcm16Le(bytes: ByteArray): ShortArray {
        val out = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(out)
        return out
    }

    private companion object {
        const val TAG = "SoundGuard.Recv"
        const val PATH_CHUNK = "/pulse/audio/stream/chunk"
        const val SAMPLE_RATE = 16_000
    }
}
