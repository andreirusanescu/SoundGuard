package com.soundguard.app.ui.auth

import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soundguard.app.auth.AuthRepository
import com.soundguard.app.auth.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isSigningIn: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository
) : ViewModel() {

    val user: StateFlow<User?> = repository.userFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    val isGoogleSignInConfigured: Boolean
        get() = repository.isGoogleSignInConfigured

    fun signInWithGoogle(activity: ComponentActivity) {
        viewModelScope.launch {
            _uiState.value = AuthUiState(isSigningIn = true)
            val signedIn = runCatching { repository.signInWithGoogle(activity) }.getOrNull()
            _uiState.value = AuthUiState(
                isSigningIn = false,
                errorMessage = if (signedIn == null) "Google sign-in failed or was cancelled." else null
            )
        }
    }

    fun continueAsGuest() {
        viewModelScope.launch {
            _uiState.value = AuthUiState(isSigningIn = true)
            repository.continueAsGuest()
            _uiState.value = AuthUiState(isSigningIn = false)
        }
    }

    fun signOut() {
        viewModelScope.launch { repository.signOut() }
    }
}
