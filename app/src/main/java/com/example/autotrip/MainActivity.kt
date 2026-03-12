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

    // AuthViewModel lives at the Activity level so it survives screen recompositions
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AutoTripTheme {
                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = "onboarding"
                ) {
                    appNavGraph(
                        navController = navController,
                        authViewModel = authViewModel
                    )
                }
            }
        }
    }
}