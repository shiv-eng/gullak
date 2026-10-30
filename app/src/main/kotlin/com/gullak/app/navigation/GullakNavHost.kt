package com.gullak.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.gullak.feature.creategoal.CreateGoalRoute
import com.gullak.feature.creategoal.createGoalScreen
import com.gullak.feature.goaldetail.GoalDetailRoute
import com.gullak.feature.goaldetail.goalDetailScreen
import com.gullak.feature.home.HomeRoute
import com.gullak.feature.home.homeScreen
import com.gullak.feature.paytmhome.PaytmHomeRoute
import com.gullak.feature.paytmhome.paytmHomeScreen
import com.gullak.feature.settings.SettingsRoute
import com.gullak.feature.settings.settingsScreen

/**
 * Wires the feature graphs together. The app opens on a stand-in Paytm home; Gullak is entered from Gold & Silver,
 * the Goal SIP banner or its card. Features never navigate to each other directly.
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
            onNewGoal = { navController.navigate(CreateGoalRoute()) },
            onOpenGoal = { goalId -> navController.navigate(GoalDetailRoute(goalId)) },
            onOpenSettings = { navController.navigate(SettingsRoute) },
        )
        homeScreen(
            onBack = { navController.popBackStack() },
            onNewGoal = { withVoice -> navController.navigate(CreateGoalRoute(startWithVoice = withVoice)) },
            onOpenGoal = { goalId -> navController.navigate(GoalDetailRoute(goalId)) },
            onOpenSettings = { navController.navigate(SettingsRoute) },
        )
        settingsScreen(onBack = { navController.popBackStack() })
        goalDetailScreen(onBack = { navController.popBackStack() })
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
