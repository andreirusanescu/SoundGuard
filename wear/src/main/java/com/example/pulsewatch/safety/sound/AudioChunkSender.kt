package com.example.pulsewatch.safety.sound

import android.content.Context
import android.util.Log
import com.example.pulsewatch.data.AUDIO_CHUNK_PATH
import com.google.android.gms.wearable.Wearable
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Streams 16-bit PCM mono audio chunks from the watch to the paired phone via Wearable
 * MessageClient.
 *
 * Design:
 *   - Resolves the connected phone node id once and caches it.
 *   - Sends each chunk asynchronously via callbacks (no blocking on the audio thread).
 *   - Invalidates the cached node id on send failure so the next attempt re-resolves.
 *
 * The wire payload is raw little-endian PCM_16BIT samples (no header). Chunks are
 * AUDIO_CHUNK_BYTES large, see WearProtocol.kt.
 */
class AudioChunkSender(private val context: Context) {

    @Volatile
    private var cachedNodeId: String? = null

    @Volatile
    private var resolving = false

    fun resolveNode(onResult: ((String?) -> Unit)? = null) {
        if (resolving) return
        resolving = true
        Wearable.getNodeClient(context).connectedNodes
            .addOnSuccessListener { nodes ->
                val best = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
                cachedNodeId = best?.id
                Log.d(TAG, "Node resolved: ${best?.displayName} id=${best?.id}")
                resolving = false
                onResult?.invoke(best?.id)
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Failed to resolve connected nodes: ${e.message}")
                resolving = false
                onResult?.invoke(null)
            }
    }

    /**
     * Fire-and-forget send. If no node cached yet, kicks off resolution and drops this chunk.
     */
    fun send(samples: ShortArray) {
        val nodeId = cachedNodeId
        if (nodeId == null) {
            Log.w(TAG, "No node cached, kicking off resolve. DROPPING chunk.")
            resolveNode()
            return
        }
        val bytes = encodeLittleEndian(samples)
        Wearable.getMessageClient(context)
            .sendMessage(nodeId, AUDIO_CHUNK_PATH, bytes)
            .addOnSuccessListener { msgId ->
                Log.d(TAG, "✓ sent ${bytes.size}B → node=$nodeId msgId=$msgId")
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "✗ Send failed (${bytes.size}B) → node=$nodeId : ${e.message}")
                cachedNodeId = null   // force re-resolve on next chunk
            }
    }

    private fun encodeLittleEndian(samples: ShortArray): ByteArray {
        val out = ByteArray(samples.size * 2)
        val buf = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) buf.putShort(s)
        return out
    }

    companion object {
        private const val TAG = "AudioChunkSender"
    }
}
