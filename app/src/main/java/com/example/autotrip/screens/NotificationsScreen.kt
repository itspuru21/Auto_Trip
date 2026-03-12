package com.example.autotrip.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.autotrip.components.AutoTripTopBar
import com.example.autotrip.ui.theme.AutoTripTheme
import com.example.autotrip.viewmodel.AppNotification
import com.example.autotrip.viewmodel.AuthViewModel
import com.example.autotrip.viewmodel.NotifType
import com.example.autotrip.viewmodel.NotificationsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    navController : NavController,
    authViewModel : AuthViewModel? = null
) {
    // Phase 3 — real ViewModel backed by Firestore
    val notifVm: NotificationsViewModel = viewModel()
    val notifications by notifVm.visibleNotifications.collectAsState()

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

            // Action bar
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
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Clear All", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            if (notifications.isEmpty()) {
                EmptyNotificationsState()
            } else {
                LazyColumn(
                    modifier            = Modifier.fillMaxSize(),
                    contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(notifications, key = { it.id }) { notif ->
                        AnimatedVisibility(
                            visible  = true,
                            enter    = fadeIn(tween(300)) + slideInVertically(tween(300)),
                            exit     = fadeOut(tween(200)) + slideOutHorizontally(tween(200))
                        ) {
                            NotificationCard(
                                notif       = notif,
                                onDismiss   = { notifVm.dismiss(notif.id) },
                                onTap       = {
                                    notif.linkedTripId?.let { id ->
                                        navController.navigate("trip_details/$id")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Clear all confirm dialog
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title  = { Text("Clear All Notifications?") },
            text   = { Text("This removes all notifications from your view. Your trips are not affected.") },
            confirmButton = {
                TextButton(onClick = { notifVm.dismissAll(); showClearConfirm = false }) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
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
private fun NotificationCard(
    notif     : AppNotification,
    onDismiss : () -> Unit,
    onTap     : () -> Unit
) {
    val (bgColor, iconVec, iconTint) = when (notif.type) {
        NotifType.TRIP_COMPLETE -> Triple(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            Icons.Default.CheckCircle,
            MaterialTheme.colorScheme.primary
        )
        NotifType.NEEDS_INFO    -> Triple(
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
            Icons.Default.Warning,
            MaterialTheme.colorScheme.error
        )
        NotifType.SYNC          -> Triple(
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
            Icons.Default.Sync,
            MaterialTheme.colorScheme.secondary
        )
        NotifType.INFO          -> Triple(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            Icons.Default.Info,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Card(
        onClick    = onTap,
        modifier   = Modifier.fillMaxWidth(),
        colors     = CardDefaults.cardColors(containerColor = bgColor),
        elevation  = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(iconVec, contentDescription = null,
                tint     = iconTint,
                modifier = Modifier.size(22.dp).padding(top = 2.dp))

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(notif.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(notif.description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(notif.timeAgo, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
            }

            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss",
                    modifier = Modifier.size(16.dp),
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.NotificationsNone, contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
            Text("All caught up!",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
            Text("No new notifications right now.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PREVIEW
// ─────────────────────────────────────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PreviewNotifications() {
    AutoTripTheme {
        NotificationScreen(rememberNavController())
    }
}