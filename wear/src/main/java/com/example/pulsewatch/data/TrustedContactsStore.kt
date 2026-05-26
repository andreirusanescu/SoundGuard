package com.example.pulsewatch.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Local cache for the trusted-contacts list synced from the phone.
 * SharedPreferences-backed so the watch keeps the list across boots even
 * when the phone isn't reachable.
 *
 * Lookups are O(1) reads of an in-memory StateFlow. The set is reloaded
 * from SharedPreferences on first access.
 */
object TrustedContactsStore {

    private const val PREFS_NAME = "trusted_contacts"
    private const val KEY = "emails"

    private val _contacts = MutableStateFlow<List<String>>(emptyList())
    val contacts: StateFlow<List<String>> = _contacts.asStateFlow()

    private var loaded = false

    @Synchronized
    fun ensureLoaded(context: Context) {
        if (loaded) return
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getStringSet(KEY, emptySet()).orEmpty()
        _contacts.value = stored.sorted()
        loaded = true
    }

    fun replace(context: Context, emails: List<String>) {
        val cleaned = emails.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSortedSet()
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY, cleaned)
            .apply()
        _contacts.value = cleaned.toList()
        loaded = true
    }

    fun add(context: Context, email: String): Boolean {
        val cleaned = email.trim().lowercase()
        if (!EMAIL_REGEX.matches(cleaned)) return false
        val merged = (_contacts.value + cleaned).toSortedSet().toList()
        replace(context, merged)
        return true
    }

    fun remove(context: Context, email: String) {
        val cleaned = email.trim().lowercase()
        replace(context, _contacts.value - cleaned)
    }

    fun snapshot(context: Context): List<String> {
        ensureLoaded(context)
        return _contacts.value
    }

    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
}
