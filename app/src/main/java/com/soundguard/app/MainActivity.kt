package com.soundguard.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.soundguard.app.auth.GmailAuthorizer
import com.soundguard.app.auth.GmailAuthorizerLauncher
import com.soundguard.app.data.AccessibilityPrefs
import com.soundguard.app.data.PreferencesRepository
import com.soundguard.app.navigation.SoundGuardNavGraph
import com.soundguard.app.ui.theme.SoundGuardTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var prefs: PreferencesRepository
    @Inject lateinit var gmailAuthorizer: GmailAuthorizer
    private lateinit var gmailLauncher: GmailAuthorizerLauncher

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* best-effort; AlertApi tolerates a missing fix */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Wire the Gmail OAuth consent flow. The launcher must be registered
        // before the activity is STARTED, hence the call here in onCreate.
        gmailLauncher = GmailAuthorizerLauncher(gmailAuthorizer)
        gmailLauncher.register(this)

        // Location is needed by AlertApi.reportAlert and AlertApi.checkLocation
        // to attach lat/lng to the crowd-sourced backend.
        val needed = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ).filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            locationPermission.launch(needed.toTypedArray())
        }

        val accessibilityFlow = prefs.accessibility
            .stateIn(lifecycleScope, SharingStarted.Eagerly, AccessibilityPrefs())
        setContent {
            val accessibility by accessibilityFlow.collectAsState()
            SoundGuardTheme(
                highContrast = accessibility.highContrast,
                largeText = accessibility.largeText
            ) {
                SoundGuardNavGraph(
                    onAuthorizeGmail = {
                        lifecycleScope.launch { gmailLauncher.launchAuthorization() }
                    }
                )
            }
        }
    }
}
