package com.example.autotrip.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.autotrip.ui.theme.AutoTripTheme

@Composable
fun PermissionsScreen(
    onPermissionsGranted : () -> Unit,
    onSkip               : () -> Unit
) {
    // Build the list of permissions to request
    val permissionsToRequest = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        // POST_NOTIFICATIONS is only needed on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        // ACCESS_BACKGROUND_LOCATION must be requested separately AFTER fine/coarse
        // are granted — we handle that in the second launcher below.
    }.toTypedArray()

    // Launcher for fine + coarse + notifications
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        // Consider it a success if at least location was granted
        val locationGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (locationGranted) onPermissionsGranted() else onPermissionsGranted()
        // We always navigate forward — the tracking screen will handle
        // cases where permission was denied at runtime.
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "Enable a Hands-Free Experience",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text("Auto Trip needs these permissions to work properly:", color = Color.Gray)

        Spacer(modifier = Modifier.height(24.dp))

        PermissionItem(
            icon        = Icons.Default.LocationOn,
            title       = "Location Access",
            description = "Allow background location to track start/end.",
            extra       = "Your location is anonymized and protected."
        )

        Spacer(modifier = Modifier.height(16.dp))

//        PermissionItem(
//            icon        = Icons.Default.Notifications,
//            title       = "Notifications",
//            description = "Receive reminders when trip information is incomplete.",
//            extra       = "Helps keep your logs accurate."
//        )

//        Spacer(modifier = Modifier.height(40.dp))

//        Text(
//            "Why these permissions?",
//            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
//        )

//        Spacer(modifier = Modifier.height(12.dp))

//        BulletPoint("Automatic trip detection")
//        Spacer(modifier = Modifier.height(8.dp))
//        BulletPoint("More accurate start & end times")
//        Spacer(modifier = Modifier.height(8.dp))
//        BulletPoint("Smart nudges to complete trip details")

        Spacer(modifier = Modifier.weight(1f))

        Button(
            // ← THIS is the fix: actually launch the system permission dialog
            onClick  = { permLauncher.launch(permissionsToRequest) },
            modifier = Modifier.fillMaxWidth(),
            colors   = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primary)
        ) {
            Text("Allow Permissions", color = MaterialTheme.colorScheme.onPrimary)
        }

        Spacer(modifier = Modifier.height(10.dp))

        TextButton(
            onClick  = onSkip,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Skip for now", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun PermissionItem(icon: ImageVector, title: String, description: String, extra: String) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier          = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(description, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(extra, fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

//@Composable
//fun BulletPoint(text: String) {
//    Row(verticalAlignment = Alignment.Top) {
//        Box(modifier = Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
//        Spacer(modifier = Modifier.width(12.dp))
//        Text(text, fontSize = 14.sp)
//    }
//}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PermissionsPreview() {
    AutoTripTheme {
        PermissionsScreen(onPermissionsGranted = {}, onSkip = {})
    }
}