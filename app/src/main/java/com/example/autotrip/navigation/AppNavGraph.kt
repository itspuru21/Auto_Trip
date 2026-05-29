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
import java.net.URLDecoder

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

    composable("home") { HomeScreen(navController, authViewModel) }

    composable("my_trips") {
        val tripsViewModel: TripsViewModel = viewModel()
        MyTripsScreen(
            navController  = navController,
            authViewModel  = authViewModel,
            tripsViewModel = tripsViewModel
        )
    }

    composable("profile") { EnhancedProfileScreen(navController) }

    // Real GPS tracking
    composable("active_tracking") {
        ActiveTrackingScreen(navController, authViewModel)
    }

    // Simulated GPS tracking — all route params passed as path segments
    composable(
        route = "active_tracking_sim/{originName}/{originLat}/{originLng}/{destName}/{destLat}/{destLng}/{mode}",
        arguments = listOf(
            navArgument("originName") { type = NavType.StringType },
            navArgument("originLat")  { type = NavType.StringType },
            navArgument("originLng")  { type = NavType.StringType },
            navArgument("destName")   { type = NavType.StringType },
            navArgument("destLat")    { type = NavType.StringType },
            navArgument("destLng")    { type = NavType.StringType },
            navArgument("mode")       { type = NavType.StringType }
        )
    ) { backStackEntry ->
        val args = backStackEntry.arguments!!
        ActiveTrackingSimScreen(
            navController = navController,
            authViewModel = authViewModel,
            originName    = URLDecoder.decode(args.getString("originName")!!, "UTF-8"),
            originLat     = args.getString("originLat")!!.toDouble(),
            originLng     = args.getString("originLng")!!.toDouble(),
            destName      = URLDecoder.decode(args.getString("destName")!!, "UTF-8"),
            destLat       = args.getString("destLat")!!.toDouble(),
            destLng       = args.getString("destLng")!!.toDouble(),
            modeName      = args.getString("mode")!!
        )
    }

    composable("dev_tools") {
        if (BuildConfig.DEBUG) {
            DevToolsScreen(navController, authViewModel)
        }
    }

    composable(
        route     = "trip_details/{tripId}",
        arguments = listOf(navArgument("tripId") { type = NavType.StringType })
    ) { backStack ->
        val tripId         = backStack.arguments?.getString("tripId") ?: return@composable
        val tripsViewModel : TripsViewModel = viewModel()
        TripDetailsScreen(
            navController  = navController,
            tripId         = tripId,
            authViewModel  = authViewModel,
            tripsViewModel = tripsViewModel
        )
    }
}