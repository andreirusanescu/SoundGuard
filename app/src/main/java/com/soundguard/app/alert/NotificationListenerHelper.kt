package com.soundguard.app.alert

import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Helpers around the system "Notification access" permission used by
 * [RoAlertListener]. Granting this permission requires the user to flip a
 * toggle in system settings — there is no runtime API to request it.
 */
object NotificationListenerHelper {

    private const val ENABLED_LISTENERS_SETTING = "enabled_notification_listeners"

    fun isEnabled(context: Context): Boolean {
        val pkg = context.packageName
        val flat = Settings.Secure.getString(context.contentResolver, ENABLED_LISTENERS_SETTING)
            ?: return false
        return flat.split(":").any { it.startsWith("$pkg/") }
    }

    fun openSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
