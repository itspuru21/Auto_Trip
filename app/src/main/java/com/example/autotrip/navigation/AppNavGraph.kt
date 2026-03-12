package com.example.autotrip.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.autotrip.screens.*

/**
 * Main navigation graph for AutoTrip.
 * Flow: onboarding → auth → permissions (signup only) → home
 */
fun NavGraphBuilder.appNavGraph(navController: NavHostController) {

    composable("onboarding") {
        OnboardingScreen(
            onContinue = { navController.navigate("auth") }
        )
    }

    composable("auth") {
        AuthScreen(
            onLoginSuccess = {
                navController.navigate("home") {
                    popUpTo("auth") { inclusive = true }
                }
            },
            onSignupSelected = {
                navController.navigate("permissions") {
                    popUpTo("auth") { inclusive = true }
                }
            }
        )
    }

    composable("permissions") {
        PermissionsScreen(
            onPermissionsGranted = {
                navController.navigate("home") {
                    popUpTo("permissions") { inclusive = true }
                }
            },
            onSkip = {
                navController.navigate("home") {
                    popUpTo("permissions") { inclusive = true }
                }
            }
        )
    }

    composable("home") { HomeScreen(navController) }
    composable("my_trips") { MyTripsScreen(navController) }
    composable("profile") { EnhancedProfileScreen(navController) }
    composable("active_tracking") { ActiveTrackingScreen(navController) }
    composable("notifications") { NotificationScreen(navController) }

    composable(
        route = "trip_details/{tripId}",
        arguments = listOf(navArgument("tripId") { type = NavType.StringType })
    ) { backStackEntry ->
        val tripId = backStackEntry.arguments?.getString("tripId").orEmpty()
        TripDetailsScreen(navController, tripId = tripId)
    }
}
