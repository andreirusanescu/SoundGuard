package com.soundguard.app.navigation

sealed class SoundGuardDestination(val route: String, val label: String) {
    data object Home : SoundGuardDestination("home", "Home")
    data object Coach : SoundGuardDestination("coach", "Coach")
    data object Health : SoundGuardDestination("health", "Health")
    data object Settings : SoundGuardDestination("settings", "Settings")

    companion object {
        val bottomNav: List<SoundGuardDestination> = listOf(Home, Coach, Health, Settings)
    }
}
