package com.shivangi.gullak.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.shivangi.gullak.feature.creategoal.CreateGoalRoute
import com.shivangi.gullak.feature.creategoal.VoiceGoalRoute
import com.shivangi.gullak.feature.creategoal.createGoalScreen
import com.shivangi.gullak.feature.creategoal.voiceGoalScreen
import com.shivangi.gullak.feature.goaldetail.GoalDetailRoute
import com.shivangi.gullak.feature.goaldetail.goalDetailScreen
import com.shivangi.gullak.feature.home.HomeRoute
import com.shivangi.gullak.feature.home.homeScreen
import com.shivangi.gullak.feature.paytmhome.PaytmHomeRoute
import com.shivangi.gullak.feature.paytmhome.paytmHomeScreen
import com.shivangi.gullak.feature.settings.SettingsRoute
import com.shivangi.gullak.feature.settings.settingsScreen

/**
 * Wires the feature graphs together. The app opens on a stand-in Paytm home; Gullak is entered from Gold & Silver,
 * the Gullak banner or its card. Features never navigate to each other directly.
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
            onNewGoal = { navController.navigate(VoiceGoalRoute) },
            onOpenGoal = { goalId -> navController.navigate(GoalDetailRoute(goalId)) },
            onOpenSettings = { navController.navigate(SettingsRoute) },
        )
        homeScreen(
            onBack = { navController.popBackStack() },
            onNewGoal = { _ -> navController.navigate(VoiceGoalRoute) },
            onOpenGoal = { goalId -> navController.navigate(GoalDetailRoute(goalId)) },
            onOpenSettings = { navController.navigate(SettingsRoute) },
        )
        settingsScreen(onBack = { navController.popBackStack() })
        goalDetailScreen(onBack = { navController.popBackStack() })
        voiceGoalScreen(
            onBack = { navController.popBackStack() },
            onTypeInstead = {
                navController.navigate(CreateGoalRoute()) {
                    popUpTo(VoiceGoalRoute) { inclusive = true }
                }
            },
            onFinish = { navController.popBackStack(PaytmHomeRoute, inclusive = false) },
            onOpenGoals = {
                navController.navigate(HomeRoute) {
                    popUpTo(PaytmHomeRoute)
                    launchSingleTop = true
                }
            },
        )
        createGoalScreen(
            onBack = { navController.popBackStack() },
            onFinish = { navController.popBackStack(PaytmHomeRoute, inclusive = false) },
            onOpenGoals = {
                navController.navigate(HomeRoute) {
                    popUpTo(PaytmHomeRoute)
                    launchSingleTop = true
                }
            },
        )
    }
}
