package com.gullak.feature.home

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

fun NavGraphBuilder.homeScreen(onNewGoal: () -> Unit) {
    composable<HomeRoute> {
        HomeScreen(onNewGoal = onNewGoal)
    }
}
