package com.example.pulsewatch.safety

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Watch-side client for the crowd-sourced alert backend (Railway / Mongo).
 * Only the zone-check is needed here — the watch never produces alerts to
 * report; that's the phone's job (see :app/.../network/AlertApi.kt).
 *
 *  POST /api/check-location  { location:{lat,lng} } -> { isDangerous, nearbyAlertsCount }
 */
object AlertApiClient {

    private const val TAG = "SoundGuard.AlertApi"
    private const val BASE_URL = "https://soundguard.up.railway.app"
    private const val TIMEOUT_MS = 5_000

    sealed class CheckResult {
        data class Done(val isDangerous: Boolean, val nearbyAlertsCount: Int) : CheckResult()
        data class Error(val message: String) : CheckResult()
        object NoLocation : CheckResult()
    }

    suspend fun checkLocation(context: Context): CheckResult = withContext(Dispatchers.IO) {
        val location = lastKnownLocation(context.applicationContext)
            ?: return@withContext CheckResult.NoLocation
        val body = JSONObject().apply {
            put("location", JSONObject().apply {
                put("lat", roundCoord(location.latitude))
                put("lng", roundCoord(location.longitude))
            })
        }.toString()
        val response = post("/api/check-location", body)
            ?: return@withContext CheckResult.Error("Network error")
        runCatching {
            val json = JSONObject(response)
            CheckResult.Done(
                isDangerous = json.optBoolean("isDangerous", false),
                nearbyAlertsCount = json.optInt("nearbyAlertsCount", 0),
            )
        }.getOrElse { CheckResult.Error("Bad response") }
    }

    private fun post(path: String, body: String): String? {
        val url = URL(BASE_URL + path)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                val err = runCatching { conn.errorStream?.bufferedReader()?.use { it.readText() } }
                    .getOrNull()
                Log.w(TAG, "POST $path -> $code ${err.orEmpty()}")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "POST $path failed: ${e.message}")
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun roundCoord(v: Double): Double =
        String.format(Locale.US, "%.6f", v).toDouble()
}
