package com.example.autotrip.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.autotrip.screens.*
import com.example.autotrip.viewmodel.AuthViewModel

fun NavGraphBuilder.appNavGraph(
    navController: NavHostController,
    authViewModel: AuthViewModel
) {

    composable("onboarding") {
        OnboardingScreen(
            onContinue = {
                navController.navigate("auth") {
                    popUpTo("onboarding") { inclusive = true }
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

    composable("home") { HomeScreen(navController, authViewModel) }
    composable("my_trips") { MyTripsScreen(navController, authViewModel) }
    composable("profile") { EnhancedProfileScreen(navController) }
    composable("active_tracking") { ActiveTrackingScreen(navController, authViewModel) }
    composable("notifications") { NotificationScreen(navController, authViewModel) }

    composable(
        route = "trip_details/{tripId}",
        arguments = listOf(navArgument("tripId") { type = NavType.StringType })
    ) { backStackEntry ->
        val tripId = backStackEntry.arguments?.getString("tripId").orEmpty()
        TripDetailsScreen(navController, tripId = tripId, authViewModel = authViewModel)
    }
}