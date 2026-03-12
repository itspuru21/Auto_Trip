package com.example.autotrip.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.autotrip.components.AutoTripTopBar
import com.example.autotrip.ui.theme.AutoTripTheme
import com.example.autotrip.viewmodel.AuthViewModel

data class NotificationItem(val id: Int, val title: String, val description: String, val timeAgo: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(navController: NavController, authViewModel: AuthViewModel? = null) {

    val notifications = remember {
        listOf(
            NotificationItem(1, "Trip Completed", "Your trip to Work was logged successfully.", "Just now"),
            NotificationItem(2, "Info Needed", "Trip to Coffee Shop needs additional info.", "10 min ago"),
            NotificationItem(3, "Sync Successful", "Your trip data synced to the server.", "2 hours ago")
        )
    }

    Scaffold(
        topBar = {
            AutoTripTopBar(
                navController = navController,
                currentRoute = "notifications",
                title = "Notifications",
                authViewModel = authViewModel
            )
        }
    ) { padding ->

        if (notifications.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { visible = true }
                AnimatedVisibility(visible = visible, enter = fadeIn(tween(700)) + scaleIn(tween(700))) {
                    Icon(Icons.Default.Notifications, contentDescription = null, tint = Color.Gray.copy(alpha = 0.5f), modifier = Modifier.size(80.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text(text = "No notifications", color = Color.Gray, style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                itemsIndexed(notifications) { index, item ->
                    var visible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { kotlinx.coroutines.delay(index * 120L); visible = true }
                    AnimatedVisibility(visible = visible, enter = slideInVertically(tween(400)) + fadeIn(tween(400))) {
                        NotificationCard(item)
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationCard(item: NotificationItem) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = item.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp)
            Spacer(Modifier.height(4.dp))
            Text(text = item.description, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            Spacer(Modifier.height(8.dp))
            Text(text = item.timeAgo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PreviewNotificationScreen() {
    AutoTripTheme { NotificationScreen(rememberNavController()) }
}