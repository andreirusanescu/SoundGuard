package com.soundguard.app.auth

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.soundguard.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authStore by preferencesDataStore("auth")

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val keyId = stringPreferencesKey("user_id")
    private val keyName = stringPreferencesKey("user_name")
    private val keyEmail = stringPreferencesKey("user_email")
    private val keyPhoto = stringPreferencesKey("user_photo")
    private val keyGuest = stringPreferencesKey("user_guest")

    val userFlow: Flow<User?> = context.authStore.data.map { prefs ->
        val id = prefs[keyId] ?: return@map null
        User(
            id = id,
            displayName = prefs[keyName] ?: "Guest",
            email = prefs[keyEmail],
            photoUrl = prefs[keyPhoto],
            isGuest = prefs[keyGuest] == "1"
        )
    }

    suspend fun currentUser(): User? = userFlow.firstOrNull()

    val isGoogleSignInConfigured: Boolean
        get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    /**
     * Triggers Credential Manager Google Sign-In. Activity is required for the system UI.
     * Returns null if Web Client ID is not configured or the user cancels.
     */
    suspend fun signInWithGoogle(activity: ComponentActivity): User? {
        if (!isGoogleSignInConfigured) return null
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        val credentialManager = CredentialManager.create(activity)
        val response = runCatching {
            credentialManager.getCredential(activity, request)
        }.getOrNull() ?: return null

        val credential = response.credential as? CustomCredential ?: return null
        if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) return null
        val tokenCredential = runCatching {
            GoogleIdTokenCredential.createFrom(credential.data)
        }.getOrNull() ?: return null

        val user = User(
            id = tokenCredential.id,
            displayName = tokenCredential.displayName ?: tokenCredential.id,
            email = tokenCredential.id.takeIf { it.contains("@") },
            photoUrl = tokenCredential.profilePictureUri?.toString(),
            isGuest = false
        )
        persist(user)
        return user
    }

    suspend fun continueAsGuest(): User {
        val user = User.guest()
        persist(user)
        return user
    }

    suspend fun signOut() {
        context.authStore.edit { it.clear() }
    }

    private suspend fun persist(user: User) {
        context.authStore.edit { prefs ->
            prefs[keyId] = user.id
            prefs[keyName] = user.displayName
            user.email?.let { prefs[keyEmail] = it }
            user.photoUrl?.let { prefs[keyPhoto] = it }
            prefs[keyGuest] = if (user.isGuest) "1" else "0"
        }
    }
}
