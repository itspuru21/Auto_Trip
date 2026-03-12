package com.example.autotrip.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

sealed class BottomNavItem(val route: String, val title: String, val icon: ImageVector) {
    object Home : BottomNavItem("home", "Home", Icons.Filled.Home)
    object MyTrips : BottomNavItem("my_trips", "My Trips", Icons.AutoMirrored.Filled.List)
    object Profile : BottomNavItem("profile", "Profile", Icons.Filled.Person)
}

@Composable
fun BottomNavigationBar(
    navController: NavController,
    currentRoute: String
) {
    val items = listOf(
        BottomNavItem.Home,
        BottomNavItem.MyTrips,
        BottomNavItem.Profile
    )

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        items.forEach { item ->
            val isSelected = currentRoute == item.route

            val scale by animateFloatAsState(
                targetValue = if (isSelected) 1.2f else 1f,
                animationSpec = tween(300),
                label = "iconScale"
            )
            val color by animateColorAsState(
                targetValue = if (isSelected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(300),
                label = "iconColor"
            )

            NavigationBarItem(
                selected = isSelected,
                onClick = {
                    if (!isSelected) {
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                                inclusive = false
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title,
                        modifier = Modifier
                            .size(26.dp)
                            .scale(scale),
                        tint = color
                    )
                },
                label = {
                    Text(text = item.title, color = color)
                }
            )
        }
    }
}