package com.soundguard.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.prefsStore by preferencesDataStore("user_prefs")

data class AccessibilityPrefs(
    val highContrast: Boolean = false,
    val largeText: Boolean = false,
    val voiceReplies: Boolean = false
)

enum class AgeBracket(val label: String) {
    UNKNOWN("Prefer not to say"),
    UNDER_25("Under 25"),
    AGE_25_40("25–40"),
    AGE_40_60("40–60"),
    OVER_60("60+")
}

enum class Environment(val label: String) {
    UNKNOWN("Prefer not to say"),
    URBAN("Urban / loud city"),
    SUBURBAN("Suburban / mixed"),
    RURAL("Rural / quiet"),
    INDOOR_OFFICE("Mostly indoors / office")
}

enum class HearingConcern(val label: String) {
    NONE("No known concern"),
    MILD_TINNITUS("Mild tinnitus / ringing"),
    PARTIAL_LOSS("Partial hearing loss"),
    USES_HEARING_AID("Uses hearing aid"),
    PROTECTING("Protecting healthy hearing")
}

data class HearingProfile(
    val displayName: String? = null,
    val ageBracket: AgeBracket = AgeBracket.UNKNOWN,
    val environment: Environment = Environment.UNKNOWN,
    val headphoneHoursPerDay: Int = 0,
    val concern: HearingConcern = HearingConcern.NONE
) {
    fun summary(): String = buildString {
        displayName?.takeIf { it.isNotBlank() }?.let { append("Name: $it. ") }
        if (ageBracket != AgeBracket.UNKNOWN) append("Age: ${ageBracket.label}. ")
        if (environment != Environment.UNKNOWN) append("Environment: ${environment.label}. ")
        if (headphoneHoursPerDay > 0) append("Headphone use: $headphoneHoursPerDay h/day. ")
        if (concern != HearingConcern.NONE) append("Hearing context: ${concern.label}.")
    }.trim().ifEmpty { "No profile shared yet." }

    val isEmpty: Boolean
        get() = displayName.isNullOrBlank() &&
            ageBracket == AgeBracket.UNKNOWN &&
            environment == Environment.UNKNOWN &&
            headphoneHoursPerDay == 0 &&
            concern == HearingConcern.NONE
}

data class DailyBriefingCache(
    val text: String,
    val generatedAtMs: Long
)

@Singleton
class PreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val keyHighContrast = booleanPreferencesKey("high_contrast")
    private val keyLargeText = booleanPreferencesKey("large_text")
    private val keyVoiceReplies = booleanPreferencesKey("voice_replies")

    private val keyProfileName = stringPreferencesKey("profile_name")
    private val keyProfileAge = stringPreferencesKey("profile_age")
    private val keyProfileEnv = stringPreferencesKey("profile_env")
    private val keyProfileHeadphoneHrs = intPreferencesKey("profile_headphone_hrs")
    private val keyProfileConcern = stringPreferencesKey("profile_concern")

    private val keyBriefingText = stringPreferencesKey("briefing_text")
    private val keyBriefingAt = longPreferencesKey("briefing_at")

    private val keyTrustedContacts = stringSetPreferencesKey("trusted_contacts")

    val accessibility: Flow<AccessibilityPrefs> = context.prefsStore.data.map { prefs ->
        AccessibilityPrefs(
            highContrast = prefs[keyHighContrast] ?: false,
            largeText = prefs[keyLargeText] ?: false,
            voiceReplies = prefs[keyVoiceReplies] ?: false
        )
    }

    val profile: Flow<HearingProfile> = context.prefsStore.data.map { prefs ->
        HearingProfile(
            displayName = prefs[keyProfileName],
            ageBracket = prefs[keyProfileAge]?.let { runCatching { AgeBracket.valueOf(it) }.getOrNull() }
                ?: AgeBracket.UNKNOWN,
            environment = prefs[keyProfileEnv]?.let { runCatching { Environment.valueOf(it) }.getOrNull() }
                ?: Environment.UNKNOWN,
            headphoneHoursPerDay = prefs[keyProfileHeadphoneHrs] ?: 0,
            concern = prefs[keyProfileConcern]?.let { runCatching { HearingConcern.valueOf(it) }.getOrNull() }
                ?: HearingConcern.NONE
        )
    }

    val briefing: Flow<DailyBriefingCache?> = context.prefsStore.data.map { prefs ->
        val text = prefs[keyBriefingText]
        val at = prefs[keyBriefingAt]
        if (text.isNullOrBlank() || at == null) null else DailyBriefingCache(text, at)
    }

    /**
     * Trusted emergency contacts (emails). Sorted alphabetically so the list
     * is stable across UI rebuilds. Persisted as a Set under the hood so we
     * never store duplicates.
     */
    val trustedContacts: Flow<List<String>> = context.prefsStore.data.map { prefs ->
        (prefs[keyTrustedContacts] ?: emptySet()).sorted()
    }

    suspend fun setHighContrast(enabled: Boolean) {
        context.prefsStore.edit { it[keyHighContrast] = enabled }
    }

    suspend fun setLargeText(enabled: Boolean) {
        context.prefsStore.edit { it[keyLargeText] = enabled }
    }

    suspend fun setVoiceReplies(enabled: Boolean) {
        context.prefsStore.edit { it[keyVoiceReplies] = enabled }
    }

    suspend fun saveProfile(profile: HearingProfile) {
        context.prefsStore.edit { p ->
            profile.displayName?.takeIf { it.isNotBlank() }?.let { p[keyProfileName] = it }
                ?: p.remove(keyProfileName)
            p[keyProfileAge] = profile.ageBracket.name
            p[keyProfileEnv] = profile.environment.name
            p[keyProfileHeadphoneHrs] = profile.headphoneHoursPerDay
            p[keyProfileConcern] = profile.concern.name
        }
    }

    suspend fun saveBriefing(text: String, atMs: Long = System.currentTimeMillis()) {
        context.prefsStore.edit {
            it[keyBriefingText] = text
            it[keyBriefingAt] = atMs
        }
    }

    suspend fun addTrustedContact(email: String) {
        val cleaned = email.trim().lowercase()
        if (cleaned.isBlank()) return
        context.prefsStore.edit { p ->
            val current = p[keyTrustedContacts] ?: emptySet()
            p[keyTrustedContacts] = current + cleaned
        }
    }

    suspend fun removeTrustedContact(email: String) {
        val cleaned = email.trim().lowercase()
        context.prefsStore.edit { p ->
            val current = p[keyTrustedContacts] ?: emptySet()
            p[keyTrustedContacts] = current - cleaned
        }
    }
}
