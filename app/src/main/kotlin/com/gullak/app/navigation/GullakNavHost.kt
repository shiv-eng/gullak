package com.gullak.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.gullak.feature.home.HomeRoute
import com.gullak.feature.home.homeScreen
import com.gullak.feature.paytmhome.PaytmHomeRoute
import com.gullak.feature.paytmhome.paytmHomeScreen

/**
 * Wires the feature graphs together. The app opens on a stand-in Paytm home; Gullak is entered from its tile in
 * Gold & Silver, like any Paytm service. Features never navigate to each other directly.
 */
@Composable
fun GullakNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = PaytmHomeRoute,
        modifier = modifier,
    ) {
        paytmHomeScreen(
            onOpenGullak = { navController.navigate(HomeRoute) },
            // Create-goal and goal detail arrive in phase 6; until then both open Gullak's home.
            onNewGoal = { navController.navigate(HomeRoute) },
            onOpenGoal = { navController.navigate(HomeRoute) },
        )
        homeScreen(
            onBack = { navController.popBackStack() },
            onNewGoal = { },
        )
    }
}
