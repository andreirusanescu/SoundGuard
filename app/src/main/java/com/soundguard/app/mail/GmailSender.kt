package com.soundguard.app.mail

import android.util.Base64
import android.util.Log
import com.soundguard.app.auth.GmailAuthorizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends emails via the Gmail REST API using the user's OAuth access token.
 *
 *   POST https://gmail.googleapis.com/gmail/v1/users/me/messages/send
 *   Authorization: Bearer <token from GmailAuthorizer>
 *   Body: { "raw": "<base64url-encoded RFC 2822 message>" }
 *
 * No SMTP credentials are stored. The FROM is whichever Google account
 * granted the gmail.send scope — i.e. the user themselves.
 */
@Singleton
class GmailSender @Inject constructor(
    private val authorizer: GmailAuthorizer,
) {
    suspend fun send(
        recipients: List<String>,
        subject: String,
        body: String,
    ): Boolean = withContext(Dispatchers.IO) {
        if (recipients.isEmpty()) {
            Log.w(TAG, "send() with no recipients — skipping")
            return@withContext false
        }
        val token = authorizer.getAccessToken()
        if (token == null) {
            Log.w(TAG, "no access token — user must authorize gmail.send first")
            return@withContext false
        }

        val ok = doSend(token, recipients, subject, body)
        if (ok) return@withContext true

        // 401 path: token expired between our cache and Gmail. Drop the
        // cache, fetch a fresh one, retry once.
        Log.i(TAG, "first attempt failed — invalidating token and retrying")
        authorizer.invalidate()
        val fresh = authorizer.getAccessToken() ?: return@withContext false
        doSend(fresh, recipients, subject, body)
    }

    private fun doSend(
        token: String,
        recipients: List<String>,
        subject: String,
        body: String,
    ): Boolean {
        val raw = buildRfc2822(recipients, subject, body)
        val rawB64 = Base64.encodeToString(
            raw.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        val payload = JSONObject().put("raw", rawB64).toString()

        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5_000
            readTimeout = 5_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        return try {
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code in 200..299) {
                Log.i(TAG, "Gmail send ok (${recipients.size} recipient(s))")
                true
            } else {
                val err = runCatching { conn.errorStream?.bufferedReader()?.use { it.readText() } }
                    .getOrNull()
                Log.w(TAG, "Gmail send -> $code ${err.orEmpty()}")
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gmail send exception: ${e.message}")
            false
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Minimal RFC 2822 message. Subject is encoded as RFC 2047 UTF-8 base64
     * so emojis in the line survive transit.
     */
    private fun buildRfc2822(
        recipients: List<String>,
        subject: String,
        body: String,
    ): String {
        val encodedSubject = "=?UTF-8?B?" +
            Base64.encodeToString(subject.toByteArray(Charsets.UTF_8), Base64.NO_WRAP) +
            "?="
        val toLine = recipients.joinToString(", ")
        return buildString {
            append("To: ").append(toLine).append("\r\n")
            append("Subject: ").append(encodedSubject).append("\r\n")
            append("MIME-Version: 1.0\r\n")
            append("Content-Type: text/plain; charset=UTF-8\r\n")
            append("Content-Transfer-Encoding: 8bit\r\n")
            append("\r\n")
            append(body)
        }
    }

    private companion object {
        const val TAG = "SoundGuard.GmailSend"
        const val ENDPOINT = "https://gmail.googleapis.com/gmail/v1/users/me/messages/send"
    }
}
