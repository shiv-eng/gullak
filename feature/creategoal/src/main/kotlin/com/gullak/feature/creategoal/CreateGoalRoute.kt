package com.gullak.feature.creategoal

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable

/** The Goal SIP flow. [startWithVoice] opens speech input straight away (the mic buttons). */
@Serializable
data class CreateGoalRoute(val startWithVoice: Boolean = false)

fun NavGraphBuilder.createGoalScreen(
    onBack: () -> Unit,
    onFinish: () -> Unit,
    onOpenGoals: () -> Unit,
) {
    composable<CreateGoalRoute> { entry ->
        CreateGoalEntry(
            startWithVoice = entry.toRoute<CreateGoalRoute>().startWithVoice,
            onBack = onBack,
            onFinish = onFinish,
            onOpenGoals = onOpenGoals,
        )
    }
}
