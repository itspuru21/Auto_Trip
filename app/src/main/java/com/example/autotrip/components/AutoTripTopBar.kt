package com.example.autotrip.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.autotrip.state.SyncState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoTripTopBar(
    navController: NavController,
    currentRoute: String,
    title: String
) {
    val scope = rememberCoroutineScope()
    val showBackButton = currentRoute != "home"

    // Pulse animation only active while syncing
    val infiniteTransition = rememberInfiniteTransition(label = "syncPulse")
    val syncScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (SyncState.isSyncing.value) 1.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAnim"
    )
    val syncTint by animateColorAsState(
        targetValue = if (SyncState.isOffline.value) Color.Red else MaterialTheme.colorScheme.primary,
        animationSpec = tween(500),
        label = "syncTint"
    )

    TopAppBar(
        title = {
            Crossfade(targetState = title, label = "titleFade") { newTitle ->
                Text(
                    text = newTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        navigationIcon = {
            if (showBackButton) {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        actions = {
            // Offline indicator dot
            if (SyncState.isOffline.value) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color.Red, CircleShape)
                        .align(Alignment.CenterVertically)
                )
            }

            // Cloud sync button with pulse animation
            IconButton(
                onClick = {
                    scope.launch {
                        SyncState.isSyncing.value = true
                        kotlinx.coroutines.delay(2000)
                        SyncState.isSyncing.value = false
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = "Sync",
                    modifier = Modifier.size(28.dp * syncScale),
                    tint = syncTint
                )
            }

            // Notifications button
            IconButton(onClick = { navController.navigate("notifications") }) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Notifications",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Direct logout button
            IconButton(
                onClick = {
                    navController.navigate("auth") {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Logout,
                    contentDescription = "Logout",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}