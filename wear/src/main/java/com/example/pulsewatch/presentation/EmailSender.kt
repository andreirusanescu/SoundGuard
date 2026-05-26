package com.example.pulsewatch.presentation

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Hands the SOS payload to the paired phone for delivery. The watch APK
 * has zero email credentials — the phone holds an OAuth token (gmail.send
 * scope, granted once by the user) and uses Gmail REST API.
 *
 *   Path: /pulse/sos/send
 *   Body: UTF-8 JSON  { to: [...], subject, body }
 *
 * Returns true if at least one paired phone acknowledged the message. The
 * actual email send is asynchronous on the phone side.
 */
object EmailSender {

    private const val TAG = "EmailSender"
    private const val PATH = "/pulse/sos/send"

    suspend fun send(
        context: Context,
        recipients: List<String>,
        subject: String,
        body: String,
    ): Boolean = withContext(Dispatchers.IO) {
        if (recipients.isEmpty()) {
            Log.w(TAG, "send() with no recipients — skipping")
            return@withContext false
        }
        val payload = JSONObject().apply {
            put("to", JSONArray(recipients))
            put("subject", subject)
            put("body", body)
        }.toString().toByteArray(Charsets.UTF_8)

        try {
            val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes)
            if (nodes.isEmpty()) {
                Log.w(TAG, "no paired phone — SOS NOT relayed")
                return@withContext false
            }
            val client = Wearable.getMessageClient(context)
            var anyOk = false
            for (node in nodes) {
                runCatching {
                    Tasks.await(client.sendMessage(node.id, PATH, payload))
                    Log.i(TAG, "SOS relayed to phone ${node.id} (${recipients.size} recipients)")
                    anyOk = true
                }.onFailure { Log.w(TAG, "sendMessage to ${node.id} failed: ${it.message}") }
            }
            anyOk
        } catch (e: Exception) {
            Log.e(TAG, "SOS relay exception: ${e.message}")
            false
        }
    }
}
