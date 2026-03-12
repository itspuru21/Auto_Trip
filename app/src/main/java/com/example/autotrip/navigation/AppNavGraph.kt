package com.example.autotrip.navigation

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.autotrip.screens.*
import com.example.autotrip.viewmodel.AuthViewModel
import com.example.autotrip.viewmodel.TripsViewModel

fun NavGraphBuilder.appNavGraph(
    navController : NavHostController,
    authViewModel : AuthViewModel
) {
    // A single TripsViewModel is shared between MyTripsScreen and TripDetailsScreen
    // so both screens react to the same Firestore flow without duplicate listeners.
    // We achieve this by hoisting the ViewModel at the NavGraph level using
    // the nav back-stack entry of a parent route (here we use the composable
    // directly — Compose ViewModel scoping handles the singleton per back-stack).

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
            authViewModel  = authViewModel,
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

    composable("home") {
        HomeScreen(navController, authViewModel)
    }

    // MyTrips and TripDetails share a TripsViewModel scoped to "my_trips"
    // back-stack entry so the Firestore listener is reused, not duplicated.
    composable("my_trips") {
        val tripsViewModel: TripsViewModel = viewModel()
        MyTripsScreen(
            navController  = navController,
            authViewModel  = authViewModel,
            tripsViewModel = tripsViewModel
        )
    }

    composable("profile") {
        EnhancedProfileScreen(navController)
    }

    composable("active_tracking") {
        ActiveTrackingScreen(navController, authViewModel)
    }

    composable("notifications") {
        NotificationScreen(navController, authViewModel)
    }

    composable(
        route     = "trip_details/{tripId}",
        arguments = listOf(navArgument("tripId") { type = NavType.StringType })
    ) { backStackEntry ->
        val tripId         = backStackEntry.arguments?.getString("tripId").orEmpty()
        val tripsViewModel : TripsViewModel = viewModel()
        TripDetailsScreen(
            navController  = navController,
            tripId         = tripId,
            authViewModel  = authViewModel,
            tripsViewModel = tripsViewModel
        )
    }
}