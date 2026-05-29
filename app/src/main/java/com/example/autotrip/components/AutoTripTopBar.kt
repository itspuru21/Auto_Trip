package com.example.autotrip.components

import androidx.compose.animation.Crossfade
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.autotrip.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoTripTopBar(
    navController : NavController,
    currentRoute  : String,
    title         : String,
    authViewModel : AuthViewModel? = null
) {
    val showBackButton = currentRoute != "home"

    TopAppBar(
        title = {
            Crossfade(targetState = title, label = "titleFade") { newTitle ->
                Text(
                    text  = newTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        navigationIcon = {
            if (showBackButton) {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(
                        imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint               = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        actions = {
            // Logout
            IconButton(
                onClick = {
                    authViewModel?.logout()
                    navController.navigate("auth") {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            ) {
                Icon(
                    imageVector        = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = "Logout",
                    tint               = MaterialTheme.colorScheme.primary
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor    = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}