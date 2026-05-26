package com.soundguard.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soundguard.app.data.AccessibilityPrefs
import com.soundguard.app.data.AgeBracket
import com.soundguard.app.data.Environment
import com.soundguard.app.data.HearingConcern
import com.soundguard.app.data.HearingProfile
import com.soundguard.app.auth.GmailAuthorizer
import com.soundguard.app.data.PreferencesRepository
import com.soundguard.app.data.TrustedContactsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: PreferencesRepository,
    private val trustedContacts: TrustedContactsRepository,
    val gmailAuthorizer: GmailAuthorizer,
) : ViewModel() {

    val gmailAuthorized: StateFlow<Boolean> = gmailAuthorizer.authorized

    val accessibility: StateFlow<AccessibilityPrefs> = prefs.accessibility
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccessibilityPrefs())

    val profile: StateFlow<HearingProfile> = prefs.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HearingProfile())

    val contacts: StateFlow<List<String>> = trustedContacts.contacts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Returns true on success. False indicates the email failed validation —
     * surfaces an inline error on the UI without throwing.
     */
    fun addContact(email: String): Boolean {
        val cleaned = email.trim()
        if (!EMAIL_REGEX.matches(cleaned)) return false
        viewModelScope.launch { trustedContacts.add(cleaned) }
        return true
    }

    fun removeContact(email: String) {
        viewModelScope.launch { trustedContacts.remove(email) }
    }

    fun setHighContrast(enabled: Boolean) {
        viewModelScope.launch { prefs.setHighContrast(enabled) }
    }

    fun setLargeText(enabled: Boolean) {
        viewModelScope.launch { prefs.setLargeText(enabled) }
    }

    fun setVoiceReplies(enabled: Boolean) {
        viewModelScope.launch { prefs.setVoiceReplies(enabled) }
    }

    fun setDisplayName(name: String) = updateProfile { it.copy(displayName = name.trim().ifEmpty { null }) }

    fun setAgeBracket(b: AgeBracket) = updateProfile { it.copy(ageBracket = b) }

    fun setEnvironment(e: Environment) = updateProfile { it.copy(environment = e) }

    fun setHeadphoneHours(h: Int) = updateProfile { it.copy(headphoneHoursPerDay = h.coerceIn(0, 16)) }

    fun setConcern(c: HearingConcern) = updateProfile { it.copy(concern = c) }

    private fun updateProfile(transform: (HearingProfile) -> HearingProfile) {
        viewModelScope.launch {
            val current = prefs.profile.firstOrNull() ?: HearingProfile()
            prefs.saveProfile(transform(current))
        }
    }
}
