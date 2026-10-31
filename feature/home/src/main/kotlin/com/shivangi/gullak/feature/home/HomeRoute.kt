package com.shivangi.gullak.feature.home

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** Gullak's own page (Gullaks), opened from the Paytm home. */
@Serializable
data object HomeRoute

fun NavGraphBuilder.homeScreen(
    onBack: () -> Unit,
    onNewGoal: (withVoice: Boolean) -> Unit,
    onOpenGoal: (goalId: String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    composable<HomeRoute> {
        HomeEntry(onBack = onBack, onNewGoal = onNewGoal, onOpenGoal = onOpenGoal, onOpenSettings = onOpenSettings)
    }
}
