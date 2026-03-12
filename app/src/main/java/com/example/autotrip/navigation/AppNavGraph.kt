package com.example.autotrip.navigation

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.autotrip.BuildConfig
import com.example.autotrip.screens.*
import com.example.autotrip.viewmodel.AuthViewModel
import com.example.autotrip.viewmodel.TripsViewModel

fun NavGraphBuilder.appNavGraph(
    navController : NavHostController,
    authViewModel : AuthViewModel
) {
    composable("onboarding") {
        OnboardingScreen(
            onContinue = {
                navController.navigate("auth") { popUpTo("onboarding") { inclusive = true } }
            }
        )
    }

    composable("auth") {
        AuthScreen(
            authViewModel    = authViewModel,
            onLoginSuccess   = {
                navController.navigate("home") { popUpTo("auth") { inclusive = true } }
            },
            onSignupSelected = {
                navController.navigate("permissions") { popUpTo("auth") { inclusive = true } }
            }
        )
    }

    composable("permissions") {
        PermissionsScreen(
            onPermissionsGranted = {
                navController.navigate("home") { popUpTo("permissions") { inclusive = true } }
            },
            onSkip = {
                navController.navigate("home") { popUpTo("permissions") { inclusive = true } }
            }
        )
    }

    composable("home") {
        HomeScreen(navController, authViewModel)
    }

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

    // ── Real GPS tracking ────────────────────────────────────────
    composable("active_tracking") {
        ActiveTrackingScreen(navController, authViewModel)
    }

    // ── Simulated GPS tracking (dev only) ────────────────────────
    // Route: active_tracking_sim/{origin}/{dest}
    // origin and dest are display names passed from DevToolsScreen.
    composable(
        route     = "active_tracking_sim/{origin}/{dest}",
        arguments = listOf(
            navArgument("origin") { type = NavType.StringType },
            navArgument("dest")   { type = NavType.StringType }
        )
    ) { backStack ->
        val origin = backStack.arguments?.getString("origin").orEmpty()
        val dest   = backStack.arguments?.getString("dest").orEmpty()
        ActiveTrackingSimScreen(
            navController = navController,
            authViewModel = authViewModel,
            simOrigin     = origin,
            simDest       = dest
        )
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

    // ── Dev Tools — only registered in debug builds ───────────────
    if (BuildConfig.DEBUG) {
        composable("dev_tools") {
            DevToolsScreen(navController, authViewModel)
        }
    }
}