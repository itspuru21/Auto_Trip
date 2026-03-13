package com.example.autotrip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.example.autotrip.navigation.appNavGraph
import com.example.autotrip.ui.theme.AutoTripTheme
import com.example.autotrip.viewmodel.AuthViewModel

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AutoTripTheme {
                val navController = rememberNavController()
                val startDestination = if (authViewModel.isAlreadyLoggedIn) "home" else "onboarding"
                NavHost(navController = navController, startDestination = startDestination) {
                    appNavGraph(navController = navController, authViewModel = authViewModel)
                }
            }
        }
    }
}