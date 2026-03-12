package com.example.autotrip.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.autotrip.screens.*
import com.example.autotrip.viewmodel.AuthViewModel

/**
 * Main navigation graph for AutoTrip.
 *
 * Flow on fresh install:   onboarding → auth → permissions (signup) → home
 * Flow if already logged in:  onboarding → home  (auth is skipped)
 */
fun NavGraphBuilder.appNavGraph(
    navController: NavHostController,
    authViewModel: AuthViewModel
) {

    composable("onboarding") {
        OnboardingScreen(
            onContinue = {
                if (authViewModel.isAlreadyLoggedIn) {
                    // User session exists — skip auth, go directly to home
                    navController.navigate("home") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                } else {
                    navController.navigate("auth")
                }
            }
        )
    }

    composable("auth") {
        AuthScreen(
            authViewModel = authViewModel,
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