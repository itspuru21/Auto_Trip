package com.example.autotrip.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.autotrip.components.AutoTripTopBar
import com.example.autotrip.ui.theme.AutoTripTheme
import com.example.autotrip.viewmodel.ActiveTrackingViewModel
import com.example.autotrip.viewmodel.AuthViewModel
import kotlinx.coroutines.delay

private enum class TrackingPhase { INPUT, TRACKING }

/**
 * Entry point for normal (real GPS) tracking.
 * Navigated to via "active_tracking" route.
 */
@Composable
fun ActiveTrackingScreen(
    navController : NavController,
    authViewModel : AuthViewModel? = null
) {
    val trackingVm: ActiveTrackingViewModel = viewModel()
    ActiveTrackingContent(
        navController = navController,
        authViewModel = authViewModel,
        trackingVm    = trackingVm,
        simOrigin     = null,
        simDest       = null
    )
}

/**
 * Entry point for simulation — navigated to via "active_tracking_sim/{origin}/{dest}".
 * The ViewModel already has [startSimulation] called by DevToolsScreen before nav.
 */
@Composable
fun ActiveTrackingSimScreen(
    navController : NavController,
    authViewModel : AuthViewModel? = null,
    simOrigin     : String,
    simDest       : String
) {
    val trackingVm: ActiveTrackingViewModel = viewModel()
    ActiveTrackingContent(
        navController = navController,
        authViewModel = authViewModel,
        trackingVm    = trackingVm,
        simOrigin     = simOrigin,
        simDest       = simDest
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// SHARED CONTENT
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ActiveTrackingContent(
    navController : NavController,
    authViewModel : AuthViewModel?,
    trackingVm    : ActiveTrackingViewModel,
    simOrigin     : String?,
    simDest       : String?
) {
    val context     = LocalContext.current
    val isSimMode   = simOrigin != null

    // If sim mode, start directly in TRACKING with pre-filled names
    var phase       by remember { mutableStateOf(if (isSimMode) TrackingPhase.TRACKING else TrackingPhase.INPUT) }
    var origin      by remember { mutableStateOf(simOrigin ?: "") }
    var destination by remember { mutableStateOf(simDest   ?: "") }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val ok = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (ok) { trackingVm.startTracking(context); phase = TrackingPhase.TRACKING }
    }

    val saveState by trackingVm.saveState.collectAsState()
    LaunchedEffect(saveState) {
        val s = saveState
        if (s is ActiveTrackingViewModel.SaveState.Saved) {
            trackingVm.resetSaveState()
            navController.navigate("trip_details/${s.tripId}") {
                popUpTo("active_tracking") { inclusive = true }
            }
        }
    }

    val isSimulating by trackingVm.isSimulating.collectAsState()

    Scaffold(
        topBar = {
            AutoTripTopBar(
                navController = navController,
                currentRoute  = "active_tracking",
                title         = if (phase == TrackingPhase.INPUT) "New Trip" else "Tracking",
                authViewModel = authViewModel
            )
        }
    ) { padding ->
        AnimatedContent(
            targetState  = phase,
            transitionSpec = {
                fadeIn(tween(350)) + slideInHorizontally(tween(350)) { it / 4 } togetherWith
                        fadeOut(tween(250)) + slideOutHorizontally(tween(250)) { -it / 4 }
            },
            label = "phaseContent"
        ) { currentPhase ->
            when (currentPhase) {
                TrackingPhase.INPUT -> {
                    TripInputPhase(
                        padding             = padding,
                        origin              = origin,
                        destination         = destination,
                        onOriginChange      = { origin = it },
                        onDestinationChange = { destination = it },
                        onStartTracking     = {
                            val fineOk = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                            if (fineOk) {
                                trackingVm.startTracking(context)
                                phase = TrackingPhase.TRACKING
                            } else {
                                permLauncher.launch(arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                ))
                            }
                        }
                    )
                }
                TrackingPhase.TRACKING -> {
                    ActiveTrackingPhase(
                        padding      = padding,
                        origin       = origin,
                        destination  = destination,
                        trackingVm   = trackingVm,
                        saveState    = saveState,
                        isSimulating = isSimulating,
                        onEndTrip    = { secs -> trackingVm.stopTrackingAndSave(origin, destination, secs) }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PHASE 1 — INPUT
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TripInputPhase(
    padding             : PaddingValues,
    origin              : String,
    destination         : String,
    onOriginChange      : (String) -> Unit,
    onDestinationChange : (String) -> Unit,
    onStartTracking     : () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(24.dp))

        Text("Where are you going?",
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Enter your start and end points to begin tracking.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))

        Spacer(Modifier.height(32.dp))

        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Origin
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(36.dp)
                        .background(Color(0xFF2E7D32).copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.size(10.dp).background(Color(0xFF2E7D32), CircleShape))
                    }
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = origin, onValueChange = onOriginChange,
                        label = { Text("Starting point") },
                        placeholder = { Text("e.g. Home, School, Office…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)) },
                        modifier = Modifier.weight(1f), singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        trailingIcon = {
                            if (origin.isNotBlank()) IconButton(onClick = { onOriginChange("") },
                                modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp)) }
                        },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary)
                    )
                }
                Box(modifier = Modifier.padding(start = 17.dp, top = 4.dp, bottom = 4.dp)
                    .width(2.dp).height(20.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)))
                // Destination
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(36.dp)
                        .background(Color(0xFFD32F2F).copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.LocationOn, null,
                            tint = Color(0xFFD32F2F), modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = destination, onValueChange = onDestinationChange,
                        label = { Text("Destination") },
                        placeholder = { Text("Work, Hospital, Mall…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)) },
                        modifier = Modifier.weight(1f), singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        trailingIcon = {
                            if (destination.isNotBlank()) IconButton(onClick = { onDestinationChange("") },
                                modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp)) }
                        },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Surface(shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Info, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp).padding(top = 1.dp))
                Text("Tracking ends when you tap Stop. Trips under 100 m won't be recorded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick  = onStartTracking,
            enabled  = origin.isNotBlank() && destination.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape    = RoundedCornerShape(16.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            Icon(Icons.Default.MyLocation, null,
                tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Start Tracking", color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PHASE 2 — ACTIVE TRACKING
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ActiveTrackingPhase(
    padding      : PaddingValues,
    origin       : String,
    destination  : String,
    trackingVm   : ActiveTrackingViewModel,
    saveState    : ActiveTrackingViewModel.SaveState,
    isSimulating : Boolean,
    onEndTrip    : (Int) -> Unit
) {
    val distanceKm by trackingVm.distanceKm.collectAsState()
    val speedKmh   by trackingVm.speedKmh.collectAsState()

    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); seconds++ } }

    val timeLabel = remember(seconds) {
        val h = seconds / 3600; val m = (seconds % 3600) / 60; val s = seconds % 60
        if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    val isSaving = saveState is ActiveTrackingViewModel.SaveState.Saving

    val infiniteTransition = rememberInfiniteTransition(label = "trackPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.35f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse"
    )

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(saveState) {
        if (saveState is ActiveTrackingViewModel.SaveState.Error)
            snackbarHostState.showSnackbar(saveState.message)
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(padding).padding(innerPadding).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            // ── Simulation banner ─────────────────────────────────
            AnimatedVisibility(visible = isSimulating) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFFF6F00).copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.BugReport, null,
                            tint = Color(0xFFFF6F00), modifier = Modifier.size(16.dp))
                        Text("Simulation running at 10× speed",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFFF6F00), fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(Modifier.height(if (isSimulating) 16.dp else 32.dp))

            // Pulse indicator
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
                Box(modifier = Modifier.size((80 * pulseScale).dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape))
                Box(modifier = Modifier.size(64.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape))
                Box(modifier = Modifier.size(44.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center) {
                    Icon(if (isSimulating) Icons.Default.BugReport else Icons.Default.MyLocation,
                        null, tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp))
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(if (isSimulating) "Simulation Active" else "Tracking Active",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(if (isSimulating) "Simulated GPS at 10× speed" else "GPS is recording your route",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))

            Spacer(Modifier.height(28.dp))

            // Route card
            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp, shadowElevation = 3.dp) {
                Column(modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RouteRow("From", origin.ifBlank { "Current Location" }, Color(0xFF2E7D32))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    RouteRow("To",   destination.ifBlank { "Destination" },  Color(0xFFD32F2F))
                }
            }

            Spacer(Modifier.height(24.dp))

            // Live stats
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                LiveStatCard("Duration", timeLabel, Icons.Default.Timer)
                LiveStatCard("Distance",
                    if (distanceKm < 0.01) "— km" else "%.2f km".format(distanceKm),
                    Icons.Default.Straighten)
                LiveStatCard("Speed",
                    if (speedKmh < 0.5) "— km/h" else "%.0f km/h".format(speedKmh),
                    Icons.Default.Speed)
            }

            Spacer(Modifier.weight(1f))

            var pressed by remember { mutableStateOf(false) }
            val btnScale by animateFloatAsState(
                targetValue = if (pressed) 0.94f else 1f, animationSpec = tween(150), label = "btn")

            Button(
                onClick = { if (!isSaving) { pressed = true; onEndTrip(seconds) } },
                enabled  = !isSaving,
                modifier = Modifier.fillMaxWidth().height(56.dp).scale(btnScale),
                shape    = RoundedCornerShape(16.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Saving…", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                } else {
                    Icon(Icons.Default.Stop, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("End Trip", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SMALL COMPOSABLES
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RouteRow(label: String, value: String, dotColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(dotColor))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun LiveStatCard(label: String, value: String, icon: ImageVector) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp, shadowElevation = 2.dp) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PreviewActiveTracking() {
    AutoTripTheme { ActiveTrackingScreen(rememberNavController()) }
}