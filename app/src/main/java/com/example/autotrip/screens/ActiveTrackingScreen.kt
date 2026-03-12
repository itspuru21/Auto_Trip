package com.example.autotrip.screens

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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.autotrip.components.AutoTripTopBar
import com.example.autotrip.ui.theme.AutoTripTheme
import com.example.autotrip.viewmodel.AuthViewModel
import kotlinx.coroutines.delay

// Two phases: INPUT (user enters route) → TRACKING (trip in progress)
private enum class TrackingPhase { INPUT, TRACKING }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveTrackingScreen(navController: NavController, authViewModel: AuthViewModel? = null) {

    var phase       by remember { mutableStateOf(TrackingPhase.INPUT) }
    var origin      by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }

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
                        padding     = padding,
                        origin      = origin,
                        destination = destination,
                        onOriginChange      = { origin = it },
                        onDestinationChange = { destination = it },
                        onStartTracking     = { phase = TrackingPhase.TRACKING }
                    )
                }
                TrackingPhase.TRACKING -> {
                    ActiveTrackingPhase(
                        padding     = padding,
                        origin      = origin,
                        destination = destination,
                        onEndTrip   = { navController.navigate("trip_details/AT-${System.currentTimeMillis()}") }
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
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Spacer(Modifier.height(24.dp))

        // Header
        Text(
            "Where are you going?",
            style      = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Enter your start and end points to begin tracking.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )

        Spacer(Modifier.height(32.dp))

        // Route input card
        Surface(
            shape          = RoundedCornerShape(20.dp),
            color          = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 4.dp,
            modifier       = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {

                // Origin field
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier         = Modifier
                            .size(36.dp)
                            .background(Color(0xFF2E7D32).copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(modifier = Modifier.size(10.dp).background(Color(0xFF2E7D32), CircleShape))
                    }
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value         = origin,
                        onValueChange = onOriginChange,
                        label         = { Text("Starting point") },
                        placeholder   = { Text("e.g. Home, School, Office…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)) },
                        modifier      = Modifier.weight(1f),
                        singleLine    = true,
                        shape         = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        trailingIcon  = {
                            if (origin.isNotBlank())
                                IconButton(onClick = { onOriginChange("") }, modifier = Modifier.size(20.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear",
                                        modifier = Modifier.size(16.dp))
                                }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                // Connector line between fields
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(24.dp)
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    // Swap button
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick  = {
                            val temp = origin
                            onOriginChange(destination)
                            onDestinationChange(temp)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.SwapVert, contentDescription = "Swap",
                            tint     = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp))
                    }
                }

                // Destination field
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier         = Modifier
                            .size(36.dp)
                            .background(Color(0xFFD32F2F).copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null,
                            tint = Color(0xFFD32F2F), modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value         = destination,
                        onValueChange = onDestinationChange,
                        label         = { Text("Destination") },
                        placeholder   = { Text("e.g. Work, Hospital, Mall…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)) },
                        modifier      = Modifier.weight(1f),
                        singleLine    = true,
                        shape         = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        trailingIcon  = {
                            if (destination.isNotBlank())
                                IconButton(onClick = { onDestinationChange("") }, modifier = Modifier.size(20.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear",
                                        modifier = Modifier.size(16.dp))
                                }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Info note about auto-detection
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ) {
            Row(
                modifier          = Modifier.padding(12.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Info, contentDescription = null,
                    tint     = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp).padding(top = 1.dp))
                Text(
                    "Tracking will end automatically when you arrive, or you can end it manually. " +
                            "Trips under 500 m will not be recorded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Start button
        val canStart = origin.isNotBlank() && destination.isNotBlank()
        Button(
            onClick   = onStartTracking,
            enabled   = canStart,
            modifier  = Modifier.fillMaxWidth().height(56.dp),
            shape     = RoundedCornerShape(16.dp),
            colors    = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            Icon(Icons.Default.MyLocation, contentDescription = null,
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
    padding     : PaddingValues,
    origin      : String,
    destination : String,
    onEndTrip   : () -> Unit
) {
    // Live timer state
    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            seconds++
        }
    }

    val timeLabel = remember(seconds) {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    // Pulsing dot animation
    val infiniteTransition = rememberInfiniteTransition(label = "trackPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.35f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse"
    )

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))

        // Pulse indicator
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
            Box(
                modifier = Modifier
                    .size((80 * pulseScale).dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
            }
        }

        Spacer(Modifier.height(16.dp))

        Text("Tracking Active", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("GPS is recording your route",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))

        Spacer(Modifier.height(28.dp))

        // Route summary card
        Surface(
            modifier       = Modifier.fillMaxWidth(),
            shape          = RoundedCornerShape(16.dp),
            color          = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 3.dp
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RouteRow(label = "From", value = origin.ifBlank { "Current Location" },
                    dotColor = Color(0xFF2E7D32))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                RouteRow(label = "To", value = destination.ifBlank { "Destination" },
                    dotColor = Color(0xFFD32F2F))
            }
        }

        Spacer(Modifier.height(24.dp))

        // Live stats
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            LiveStatCard(label = "Duration", value = timeLabel, icon = Icons.Default.Timer)
            LiveStatCard(label = "Distance", value = "— km",    icon = Icons.Default.Straighten)
            LiveStatCard(label = "Speed",    value = "— km/h",  icon = Icons.Default.Speed)
        }

        Spacer(Modifier.weight(1f))

        // End trip button
        var pressed by remember { mutableStateOf(false) }
        val scale by animateFloatAsState(
            targetValue   = if (pressed) 0.94f else 1f,
            animationSpec = tween(150), label = "btnScale"
        )
        Button(
            onClick   = { pressed = true; onEndTrip() },
            modifier  = Modifier.fillMaxWidth().height(56.dp).scale(scale),
            shape     = RoundedCornerShape(16.dp),
            colors    = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            Icon(Icons.Default.Stop, contentDescription = null,
                tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("End Trip", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RouteRow(label: String, value: String, dotColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(modifier = Modifier.size(10.dp).background(dotColor, CircleShape))
        Column {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun LiveStatCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(
        shape          = RoundedCornerShape(14.dp),
        color          = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        modifier       = Modifier.width(100.dp)
    ) {
        Column(
            modifier            = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp))
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PreviewActiveTrackingScreen() {
    AutoTripTheme { ActiveTrackingScreen(rememberNavController()) }
}