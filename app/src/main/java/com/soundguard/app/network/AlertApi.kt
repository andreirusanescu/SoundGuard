package com.soundguard.app.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Crowd-sourced alert backend (Railway-hosted Mongo).
 *
 *  POST /api/alerts          { alertType, severity, location:{lat,lng} }
 *  POST /api/check-location  { location:{lat,lng} } -> { isDangerous, nearbyAlertsCount }
 *
 * The server increments reportCount itself when the same alertType lands at
 * the same location — clients post once per detection and don't dedupe on
 * the wire.
 */
object AlertApi {

    private const val TAG = "SoundGuard.AlertApi"
    private const val BASE_URL = "https://soundguard.up.railway.app"
    private const val TIMEOUT_MS = 5_000

    sealed class CheckResult {
        data class Done(val isDangerous: Boolean, val nearbyAlertsCount: Int) : CheckResult()
        data class Error(val message: String) : CheckResult()
        object NoLocation : CheckResult()
    }

    /**
     * Reports a detection. The phone has already passed the local 5s debounce
     * by the time this is called. Fire-and-forget; failures are logged only.
     */
    suspend fun reportAlert(
        context: Context,
        alertType: String,
        severity: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val location = lastKnownLocation(context.applicationContext)
        if (location == null) {
            Log.w(TAG, "reportAlert($alertType) skipped — no GPS fix")
            return@withContext false
        }
        val body = JSONObject().apply {
            put("alertType", alertType)
            put("severity", severity)
            put("location", JSONObject().apply {
                put("lat", roundCoord(location.latitude))
                put("lng", roundCoord(location.longitude))
            })
        }.toString()
        val ok = post("/api/alerts", body) != null
        Log.i(TAG, "reportAlert($alertType, $severity) -> ok=$ok")
        ok
    }

    /**
     * Asks the server whether the device's current GPS fix sits inside a
     * cluster of recent alerts. Returns [CheckResult.NoLocation] when the
     * device has no cached fix to send.
     */
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

    // 6 decimals ≈ 11 cm — plenty for a city-block-scale danger map and
    // stops us from oversharing exact coordinates.
    private fun roundCoord(v: Double): Double =
        String.format(Locale.US, "%.6f", v).toDouble()
}
