package com.gullak.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.gullak.feature.home.HomeRoute
import com.gullak.feature.home.homeScreen

/** Wires the feature graphs together. Features never navigate to each other directly. */
@Composable
fun GullakNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = modifier,
    ) {
        homeScreen(
            onNewGoal = { /* Create-goal flow arrives in phase 6. */ },
        )
    }
}
