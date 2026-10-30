package com.gullak.feature.goaldetail

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** One Goal SIP. [goalId] is read by [GoalDetailViewModel] from the saved state. */
@Serializable
data class GoalDetailRoute(val goalId: String)

fun NavGraphBuilder.goalDetailScreen(onBack: () -> Unit) {
    composable<GoalDetailRoute> {
        GoalDetailEntry(onBack = onBack)
    }
}
