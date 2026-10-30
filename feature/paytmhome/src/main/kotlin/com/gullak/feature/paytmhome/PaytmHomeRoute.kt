package com.gullak.feature.paytmhome

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** The stand-in Paytm home: the app's start screen, with Gullak beside Gold & Silver. */
@Serializable
data object PaytmHomeRoute

fun NavGraphBuilder.paytmHomeScreen(
    onOpenGullak: () -> Unit,
    onNewGoal: () -> Unit,
    onOpenGoal: (goalId: String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    composable<PaytmHomeRoute> {
        PaytmHomeEntry(
            onOpenGullak = onOpenGullak,
            onNewGoal = onNewGoal,
            onOpenGoal = onOpenGoal,
            onOpenSettings = onOpenSettings,
        )
    }
}
