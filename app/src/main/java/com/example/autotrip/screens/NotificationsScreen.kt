package com.example.autotrip.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.autotrip.components.AutoTripTopBar
import com.example.autotrip.ui.theme.AutoTripTheme
import com.example.autotrip.viewmodel.AuthViewModel
import kotlinx.coroutines.delay

data class NotificationItem(
    val id          : Int,
    val title       : String,
    val description : String,
    val timeAgo     : String,
    val type        : NotifType = NotifType.INFO
)

enum class NotifType { TRIP_COMPLETE, NEEDS_INFO, SYNC, INFO }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(navController: NavController, authViewModel: AuthViewModel? = null) {

    // In Phase 3 this will come from a NotificationsViewModel backed by Firestore / local DB.
    // For now we use local mutable state so Clear All works correctly in the UI.
    var notifications by remember {
        mutableStateOf(
            listOf(
                NotificationItem(1, "Trip Completed",
                    "Your trip from Home to Work was logged successfully.", "Just now",
                    NotifType.TRIP_COMPLETE),
                NotificationItem(2, "Info Needed",
                    "Trip to Coffee Shop needs additional details — tap to complete.", "10 min ago",
                    NotifType.NEEDS_INFO),
                NotificationItem(3, "Sync Successful",
                    "All trip data has been synced to the NATPAC server.", "2 hours ago",
                    NotifType.SYNC),
                NotificationItem(4, "Trip Completed",
                    "Your trip from Work to Gym was recorded (3.2 km, 18 min).", "3 hours ago",
                    NotifType.TRIP_COMPLETE)
            )
        )
    }

    var showClearConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AutoTripTopBar(
                navController = navController,
                currentRoute  = "notifications",
                title         = "Notifications",
                authViewModel = authViewModel
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // Action bar — only show when there are notifications
            AnimatedVisibility(visible = notifications.isNotEmpty()) {
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        "${notifications.size} notification${if (notifications.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    TextButton(
                        onClick = { showClearConfirm = true },
                        colors  = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Clear All", fontSize = 13.sp)
                    }
                }
            }

            if (notifications.isEmpty()) {
                EmptyNotificationsState()
            } else {
                LazyColumn(
                    modifier        = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding  = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    itemsIndexed(
                        items = notifications,
                        key   = { _, item -> item.id }
                    ) { index, item ->
                        var visible by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { delay(index * 100L); visible = true }

                        AnimatedVisibility(
                            visible    = visible,
                            enter      = slideInVertically(tween(380)) { it / 2 } + fadeIn(tween(380)),
                            exit       = slideOutHorizontally(tween(280)) { it } + fadeOut(tween(200))
                        ) {
                            NotificationCard(
                                item     = item,
                                onDismiss = {
                                    notifications = notifications.filter { it.id != item.id }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Clear All confirmation
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            icon  = {
                Icon(Icons.Default.DeleteSweep, contentDescription = null,
                    tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp))
            },
            title = { Text("Clear All Notifications?", fontWeight = FontWeight.Bold) },
            text  = { Text("This will remove all ${notifications.size} notifications. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = { notifications = emptyList(); showClearConfirm = false },
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Clear All") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// NOTIFICATION CARD
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun NotificationCard(item: NotificationItem, onDismiss: () -> Unit) {
    val (accentColor, bgColor) = when (item.type) {
        NotifType.TRIP_COMPLETE -> Pair(Color(0xFF2E7D32), Color(0xFF2E7D32).copy(alpha = 0.08f))
        NotifType.NEEDS_INFO    -> Pair(Color(0xFFFF8F00), Color(0xFFFF8F00).copy(alpha = 0.08f))
        NotifType.SYNC          -> Pair(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
        NotifType.INFO          -> Pair(MaterialTheme.colorScheme.outline,  Color.Transparent)
    }

    Card(
        shape     = MaterialTheme.shapes.medium,
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(3.dp),
        modifier  = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier          = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Color accent bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(52.dp)
                    .then(
                        Modifier.padding(top = 2.dp)
                    )
            ) {
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRoundRect(
                        color        = accentColor,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(3.dp))
                Text(item.description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Text(item.timeAgo, style = MaterialTheme.typography.labelSmall, color = accentColor)
            }

            // Dismiss (×) button
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss",
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// EMPTY STATE
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyNotificationsState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { visible = true }
        AnimatedVisibility(visible = visible, enter = fadeIn(tween(600)) + scaleIn(tween(600))) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.NotificationsNone, contentDescription = null,
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(72.dp))
                Spacer(Modifier.height(12.dp))
                Text("All caught up!", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
                Spacer(Modifier.height(4.dp))
                Text("No notifications right now",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PreviewNotificationScreen() {
    AutoTripTheme { NotificationScreen(rememberNavController()) }
}