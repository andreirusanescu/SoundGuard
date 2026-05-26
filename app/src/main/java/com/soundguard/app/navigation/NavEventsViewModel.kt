package com.soundguard.app.navigation

import androidx.lifecycle.ViewModel
import com.soundguard.app.ai.CoachIntent
import com.soundguard.app.ai.CoachRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Lives at the MainScaffold scope — long-lived enough to observe cross-screen
 * `CoachIntent`s emitted by Home/Health and route the NavHost to the Coach tab.
 */
@HiltViewModel
class NavEventsViewModel @Inject constructor(
    private val coachRepository: CoachRepository
) : ViewModel() {
    val coachIntents: Flow<CoachIntent> = coachRepository.intents
}
