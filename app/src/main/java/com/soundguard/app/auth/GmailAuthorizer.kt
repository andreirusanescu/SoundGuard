package com.soundguard.app.auth

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.soundguard.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Wraps [Identity.getAuthorizationClient] for the Gmail send scope.
 *
 *   scope: https://www.googleapis.com/auth/gmail.send
 *
 * Holds NO credentials — the access token is obtained from Google at runtime
 * after the user grants consent once. Token is cached in memory; on a 401
 * response the caller is expected to call [invalidate] and retry once.
 *
 * The user-visible flow is:
 *   1) Settings → "Authorize Gmail SOS" button.
 *   2) Activity calls [requestAuthorization]. If consent is needed Google
 *      returns a PendingIntent — the activity launches it via the registered
 *      [ActivityResultContracts.StartIntentSenderForResult] launcher and feeds
 *      the result back into [handleConsentResult].
 *   3) From then on [getAccessToken] returns a valid Bearer token directly.
 */
@Singleton
class GmailAuthorizer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val client by lazy { Identity.getAuthorizationClient(context) }

    private val _authorized = MutableStateFlow(false)
    val authorized: StateFlow<Boolean> = _authorized.asStateFlow()

    @Volatile private var cachedToken: String? = null

    /**
     * Try to obtain an access token without UI. Returns null if consent is
     * required — caller should then route through [requestAuthorization].
     */
    suspend fun getAccessToken(): String? {
        cachedToken?.let { return it }
        val result = runCatching { authorizeOnce() }.getOrNull() ?: return null
        if (result.hasResolution()) {
            // consent is needed — caller has to go through the activity flow.
            _authorized.value = false
            return null
        }
        cachedToken = result.accessToken
        _authorized.value = cachedToken != null
        return cachedToken
    }

    /**
     * Force the consent flow (or no-op if already authorized). Returns the
     * AuthorizationResult so the activity can decide whether to launch the
     * embedded PendingIntent.
     */
    suspend fun requestAuthorization(): AuthorizationResult? =
        runCatching { authorizeOnce() }.getOrNull()

    fun handleConsentResult(intent: Intent?): String? {
        if (intent == null) return null
        return runCatching {
            val result = client.getAuthorizationResultFromIntent(intent)
            cachedToken = result.accessToken
            _authorized.value = cachedToken != null
            cachedToken
        }.onFailure { Log.w(TAG, "handleConsentResult failed: ${it.message}") }
            .getOrNull()
    }

    fun invalidate() {
        cachedToken = null
    }

    private suspend fun authorizeOnce(): AuthorizationResult =
        suspendCancellableCoroutine { cont ->
            val request = AuthorizationRequest.Builder()
                .setRequestedScopes(listOf(Scope(GMAIL_SEND_SCOPE)))
                .apply {
                    val webId = BuildConfig.GOOGLE_WEB_CLIENT_ID
                    if (webId.isNotBlank()) requestOfflineAccess(webId)
                }
                .build()
            client.authorize(request)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

    companion object {
        private const val TAG = "GmailAuthorizer"
        const val GMAIL_SEND_SCOPE = "https://www.googleapis.com/auth/gmail.send"
    }
}

/**
 * Helper that wires [GmailAuthorizer] into a [ComponentActivity]. Call
 * [register] from `onCreate`, then call [launchAuthorization] when the user
 * taps the Authorize button.
 */
class GmailAuthorizerLauncher(
    private val authorizer: GmailAuthorizer,
) {
    private var consentLauncher: androidx.activity.result.ActivityResultLauncher<IntentSenderRequest>? = null

    fun register(activity: ComponentActivity, onResult: (Boolean) -> Unit = {}) {
        consentLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { res ->
            val token = authorizer.handleConsentResult(res.data)
            onResult(token != null)
        }
    }

    suspend fun launchAuthorization() {
        val result = authorizer.requestAuthorization() ?: return
        if (result.hasResolution()) {
            val pendingIntent = result.pendingIntent ?: return
            val request = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
            consentLauncher?.launch(request)
        }
        // else: already authorized — token is cached.
    }
}
