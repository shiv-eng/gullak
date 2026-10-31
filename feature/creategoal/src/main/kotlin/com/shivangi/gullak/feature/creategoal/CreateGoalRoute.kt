package com.shivangi.gullak.feature.creategoal

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.shivangi.gullak.feature.creategoal.voice.VoiceGoalEntry
import kotlinx.serialization.Serializable

/** The Gullak flow. [startWithVoice] opens speech input straight away (the mic buttons). */
@Serializable
data class CreateGoalRoute(val startWithVoice: Boolean = false)

/** Starting a Gullak by talking: the main way in. */
@Serializable
data object VoiceGoalRoute

fun NavGraphBuilder.voiceGoalScreen(
    onBack: () -> Unit,
    onTypeInstead: () -> Unit,
    onFinish: () -> Unit,
    onOpenGoals: () -> Unit,
) {
    composable<VoiceGoalRoute> {
        VoiceGoalEntry(onBack = onBack, onTypeInstead = onTypeInstead, onFinish = onFinish, onOpenGoals = onOpenGoals)
    }
}

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
