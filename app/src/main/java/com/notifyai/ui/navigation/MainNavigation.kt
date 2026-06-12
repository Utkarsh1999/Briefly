package com.notifyai.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.notifyai.ui.history.NotificationHistoryScreen
import com.notifyai.ui.home.HomeScreen
import com.notifyai.ui.insights.InsightsScreen
import com.notifyai.ui.onboarding.OnboardingScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object History : Screen("history")
    object Insights : Screen("insights")
    object Onboarding : Screen("onboarding")
}

@Composable
fun MainNavigation(hasNotificationPermission: Boolean) {
    val navController = rememberNavController()

    val startDestination = if (hasNotificationPermission) Screen.Home.route else Screen.Onboarding.route

    NavHost(navController = navController, startDestination = startDestination) {
        
        composable(Screen.Onboarding.route) {
            OnboardingScreen()
        }

        composable(Screen.Home.route) {
            HomeScreen(
                navigateToHistory = { navController.navigate(Screen.History.route) },
                navigateToInsights = { navController.navigate(Screen.Insights.route) }
            )
        }

        composable(Screen.History.route) {
            NotificationHistoryScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Insights.route) {
            InsightsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
