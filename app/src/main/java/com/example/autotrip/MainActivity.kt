package com.example.autotrip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.example.autotrip.navigation.appNavGraph
import com.example.autotrip.service.TrackingService
import com.example.autotrip.ui.theme.AutoTripTheme
import com.example.autotrip.viewmodel.AuthViewModel

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AutoTripTheme {
                val navController = rememberNavController()
                val startDestination = resolveStartDestination()

                NavHost(navController = navController, startDestination = startDestination) {
                    appNavGraph(navController = navController, authViewModel = authViewModel)
                }
            }
        }
    }

    /**
     * If the TrackingService saved a trip via the notification button while the app
     * was backgrounded, it wrote the tripId to SharedPreferences. We pick that up
     * here and deep-link straight to TripDetailsScreen so the user can complete the
     * trip info without having to navigate manually.
     */
    private fun resolveStartDestination(): String {
        if (!authViewModel.isAlreadyLoggedIn) return "onboarding"

        val prefs  = getSharedPreferences(TrackingService.PREFS_NAME, MODE_PRIVATE)
        val tripId = prefs.getString(TrackingService.KEY_PENDING_TRIP, null)
        if (!tripId.isNullOrBlank()) {
            prefs.edit().remove(TrackingService.KEY_PENDING_TRIP).apply()
            return "trip_details/$tripId"
        }

        return "home"
    }
}