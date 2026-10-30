package com.gullak.feature.home

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** Gullak's own home, opened from the Gullak tile on the Paytm home. */
@Serializable
data object HomeRoute

fun NavGraphBuilder.homeScreen(
    onBack: () -> Unit,
    onNewGoal: () -> Unit,
) {
    composable<HomeRoute> {
        HomeScreen(onBack = onBack, onNewGoal = onNewGoal)
    }
}
