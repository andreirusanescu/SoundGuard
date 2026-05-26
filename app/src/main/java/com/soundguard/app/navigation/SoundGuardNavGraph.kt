package com.soundguard.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.soundguard.app.ui.auth.AuthViewModel
import com.soundguard.app.ui.auth.SignInScreen
import com.soundguard.app.ui.coach.CoachScreen
import com.soundguard.app.ui.health.HealthScreen
import com.soundguard.app.ui.home.HomeScreen
import com.soundguard.app.ui.settings.SettingsScreen

@Composable
fun SoundGuardNavGraph(
    onAuthorizeGmail: () -> Unit = {},
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val user by authViewModel.user.collectAsStateWithLifecycle()
    if (user == null) {
        SignInScreen(viewModel = authViewModel)
    } else {
        MainScaffold(onAuthorizeGmail = onAuthorizeGmail)
    }
}

@Composable
private fun MainScaffold(
    onAuthorizeGmail: () -> Unit,
    navEvents: NavEventsViewModel = hiltViewModel()
) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination

    LaunchedEffect(Unit) {
        navEvents.coachIntents.collect {
            navigateToTab(nav, SoundGuardDestination.Coach.route)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                SoundGuardDestination.bottomNav.forEach { dest ->
                    val selected = current?.hierarchy?.any { it.route == dest.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navigateToTab(nav, dest.route) },
                        icon = { Icon(iconFor(dest), contentDescription = dest.label) },
                        label = { Text(dest.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = SoundGuardDestination.Home.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(SoundGuardDestination.Home.route) { HomeScreen() }
            composable(SoundGuardDestination.Coach.route) { CoachScreen() }
            composable(SoundGuardDestination.Health.route) { HealthScreen() }
            composable(SoundGuardDestination.Settings.route) {
                SettingsScreen(onAuthorizeGmail = onAuthorizeGmail)
            }
        }
    }
}

private fun navigateToTab(nav: NavHostController, route: String) {
    nav.navigate(route) {
        popUpTo(nav.graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun iconFor(d: SoundGuardDestination): ImageVector = when (d) {
    SoundGuardDestination.Home -> Icons.Filled.GraphicEq
    SoundGuardDestination.Coach -> Icons.Filled.AutoAwesome
    SoundGuardDestination.Health -> Icons.Filled.Hearing
    SoundGuardDestination.Settings -> Icons.Filled.Settings
}
