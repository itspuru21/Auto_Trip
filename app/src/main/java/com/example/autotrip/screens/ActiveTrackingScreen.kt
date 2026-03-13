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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.autotrip.components.AutoTripTopBar
import com.example.autotrip.location.SimulatedLocationProvider
import com.example.autotrip.simulation.SimMode
import com.example.autotrip.simulation.SimPreset
import com.example.autotrip.ui.theme.AutoTripTheme
import com.example.autotrip.viewmodel.ActiveTrackingViewModel
import com.example.autotrip.viewmodel.AuthViewModel
import kotlinx.coroutines.delay
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

private enum class TrackingPhase { INPUT, TRACKING }

// ─────────────────────────────────────────────────────────────────────────────
// ENTRY POINTS
// ─────────────────────────────────────────────────────────────────────────────

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

@Composable
fun ActiveTrackingSimScreen(
    navController : NavController,
    authViewModel : AuthViewModel? = null,
    originName    : String,
    originLat     : Double,
    originLng     : Double,
    destName      : String,
    destLat       : Double,
    destLng       : Double,
    modeName      : String
) {
    val trackingVm: ActiveTrackingViewModel = viewModel()

    LaunchedEffect(Unit) {
        val origin = SimPreset(originName, originLat, originLng)
        val dest   = SimPreset(destName,   destLat,   destLng)
        val mode   = SimMode.entries.firstOrNull { it.name == modeName } ?: SimMode.CAR

        val provider = SimulatedLocationProvider(origin, dest, mode)
        trackingVm.startSimulation(provider)
    }

    ActiveTrackingContent(
        navController = navController,
        authViewModel = authViewModel,
        trackingVm    = trackingVm,
        simOrigin     = originName,
        simDest       = destName
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
    val context   = LocalContext.current
    val isSimMode = simOrigin != null

    var phase       by remember { mutableStateOf(if (isSimMode) TrackingPhase.TRACKING else TrackingPhase.INPUT) }
    var origin      by remember { mutableStateOf(simOrigin ?: "") }
    var destination by remember { mutableStateOf(simDest ?: "") }

    val saveState   by trackingVm.saveState.collectAsState()
    val isSimulating by trackingVm.isSimulating.collectAsState()

    // Navigate to trip details once saved
    LaunchedEffect(saveState) {
        if (saveState is ActiveTrackingViewModel.SaveState.Saved) {
            val tripId = (saveState as ActiveTrackingViewModel.SaveState.Saved).tripId
            navController.navigate("trip_details/$tripId") {
                popUpTo("active_tracking") { inclusive = true }
                popUpTo("active_tracking_sim/{originName}/{originLat}/{originLng}/{destName}/{destLat}/{destLng}/{mode}") {
                    inclusive = true
                }
            }
        }
    }

    val fine   = Manifest.permission.ACCESS_FINE_LOCATION
    val coarse = Manifest.permission.ACCESS_COARSE_LOCATION
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* handled by start button */ }

    Scaffold(
        topBar = {
            AutoTripTopBar(
                navController = navController,
                currentRoute  = "active_tracking",
                title         = if (isSimMode) "Simulation" else "Live Tracking",
                authViewModel = authViewModel
            )
        }
    ) { padding ->
        when (phase) {
            TrackingPhase.INPUT -> InputPhase(
                padding             = padding,
                origin              = origin,
                destination         = destination,
                onOriginChange      = { origin = it },
                onDestinationChange = { destination = it },
                onDiscard           = { navController.popBackStack() },
                onStartTracking     = {
                    val hasFine = ContextCompat.checkSelfPermission(context, fine) ==
                            PackageManager.PERMISSION_GRANTED
                    if (hasFine) {
                        trackingVm.startTracking(context)
                        phase = TrackingPhase.TRACKING
                    } else {
                        permLauncher.launch(arrayOf(fine, coarse))
                    }
                }
            )
            TrackingPhase.TRACKING -> ActiveTrackingPhase(
                padding      = padding,
                origin       = origin,
                destination  = destination,
                trackingVm   = trackingVm,
                saveState    = saveState,
                isSimulating = isSimulating,
                onEndTrip    = { secs ->
                    trackingVm.stopTrackingAndSave(origin, destination, secs)
                },
                onDiscard    = {
                    trackingVm.discardTrip()
                    navController.popBackStack()
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PHASE 1 — INPUT
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun InputPhase(
    padding             : PaddingValues,
    origin              : String,
    destination         : String,
    onOriginChange      : (String) -> Unit,
    onDestinationChange : (String) -> Unit,
    onDiscard           : () -> Unit,
    onStartTracking     : () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text("Where are you going?",
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Enter your start and end points to begin tracking.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value         = origin,
            onValueChange = onOriginChange,
            label         = { Text("Start point") },
            leadingIcon   = { Icon(Icons.Default.LocationOn, null, tint = Color(0xFF2E7D32)) },
            singleLine    = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier      = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value         = destination,
            onValueChange = onDestinationChange,
            label         = { Text("Destination") },
            leadingIcon   = { Icon(Icons.Default.Flag, null, tint = Color(0xFFD32F2F)) },
            singleLine    = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier      = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.weight(1f))

        Button(
            onClick   = onStartTracking,
            enabled   = origin.isNotBlank() && destination.isNotBlank(),
            modifier  = Modifier.fillMaxWidth().height(56.dp),
            shape     = RoundedCornerShape(16.dp),
            colors    = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            Icon(Icons.Default.MyLocation, null,
                tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Start Tracking", color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick  = onDiscard,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape    = RoundedCornerShape(16.dp),
            colors   = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Discard", fontWeight = FontWeight.SemiBold)
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
    onEndTrip    : (Int) -> Unit,
    onDiscard    : () -> Unit
) {
    val distanceKm  by trackingVm.distanceKm.collectAsState()
    val speedKmh    by trackingVm.speedKmh.collectAsState()
    val routePoints by trackingVm.routePoints.collectAsState()
    val isLoading   by trackingVm.isLoading.collectAsState()

    // UI timer ticks in real time; simulation moves at 10×
    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); seconds++ } }

    // Display: for sim show "real equivalent" time; for real show actual
    val displaySeconds = if (isSimulating) seconds * 10 else seconds
    val timeLabel = remember(displaySeconds) {
        val h = displaySeconds / 3600; val m = (displaySeconds % 3600) / 60; val s = displaySeconds % 60
        if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    val pulseAnim  = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseAnim.animateFloat(
        1f, 1.15f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulseScale"
    )

    Column(
        modifier            = Modifier.fillMaxSize().padding(padding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))

        // Live map
        if (routePoints.isNotEmpty()) {
            LiveRouteMap(
                routePoints = routePoints,
                modifier    = Modifier.fillMaxWidth().height(180.dp)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
            Spacer(Modifier.height(16.dp))
        }

        // Pulsing dot
        Box(
            modifier = Modifier.size(56.dp * pulseScale).clip(CircleShape)
                .background(
                    if (isSimulating) Color(0xFF1565C0).copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isSimulating) Icons.Default.Speed else Icons.Default.GpsFixed,
                contentDescription = null,
                tint     = if (isSimulating) Color(0xFF1565C0) else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            if (isSimulating) "Simulation Active" else "Tracking Active",
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (isSimulating) "Simulated GPS at 10× speed" else "GPS is recording your route",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )

        Spacer(Modifier.height(20.dp))

        // Route card
        Surface(
            modifier       = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape          = RoundedCornerShape(16.dp),
            color          = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp, shadowElevation = 3.dp
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RouteRow("From", origin.ifBlank { "Current Location" }, Color(0xFF2E7D32))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                RouteRow("To",   destination.ifBlank { "Unknown" },     Color(0xFFD32F2F))
            }
        }

        Spacer(Modifier.height(20.dp))

        // Stats
        Row(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard("Time",     timeLabel,                     Icons.Default.Timer,  Modifier.weight(1f))
            StatCard("Distance", "%.2f km".format(distanceKm),  Icons.Default.Route,  Modifier.weight(1f))
            StatCard("Speed",    "%.1f km/h".format(speedKmh),  Icons.Default.Speed,  Modifier.weight(1f))
        }

        Spacer(Modifier.weight(1f))

        val isSaving = saveState is ActiveTrackingViewModel.SaveState.Saving

        // End trip button
        Button(
            onClick   = { onEndTrip(seconds) },
            enabled   = !isSaving,
            modifier  = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(56.dp),
            shape     = RoundedCornerShape(16.dp),
            colors    = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            if (isSaving) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
            } else {
                Icon(Icons.Default.Stop, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("End Trip", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
        }

        Spacer(Modifier.height(10.dp))

        // Discard button
        OutlinedButton(
            onClick  = onDiscard,
            enabled  = !isSaving,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp),
            shape    = RoundedCornerShape(16.dp),
            colors   = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
        ) {
            Icon(Icons.Default.DeleteOutline, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Discard Trip", fontWeight = FontWeight.Medium)
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LIVE MAP WIDGET
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LiveRouteMap(
    routePoints : List<Pair<Double, Double>>,
    modifier    : Modifier = Modifier
) {
    val context = LocalContext.current
    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(false)
            isClickable = false
        }
    }

    AndroidView(
        factory = { mapView },
        update  = { mv ->
            mv.overlays.clear()
            val geo = routePoints.map { GeoPoint(it.first, it.second) }
            if (geo.isNotEmpty()) {
                val line = Polyline().apply {
                    setPoints(geo)
                    outlinePaint.color       = android.graphics.Color.parseColor("#1565C0")
                    outlinePaint.strokeWidth = 8f
                }
                mv.overlays.add(line)

                // Green start
                mv.overlays.add(Marker(mv).apply {
                    position = geo.first(); title = "Start"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    icon = context.getDrawable(org.osmdroid.library.R.drawable.marker_default)
                        ?.apply { setTint(android.graphics.Color.parseColor("#2E7D32")) }
                })
                // Red current position
                mv.overlays.add(Marker(mv).apply {
                    position = geo.last(); title = "Current"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    icon = context.getDrawable(org.osmdroid.library.R.drawable.marker_default)
                        ?.apply { setTint(android.graphics.Color.parseColor("#D32F2F")) }
                })

                mv.controller.animateTo(geo.last())
                mv.controller.setZoom(15.0)
                mv.invalidate()
            }
        },
        modifier = modifier
    )

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose { mapView.onPause() }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SMALL HELPERS
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RouteRow(label: String, value: String, dotColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(dotColor))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Surface(
        modifier       = modifier,
        shape          = RoundedCornerShape(14.dp),
        color          = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp, shadowElevation = 2.dp
    ) {
        Column(
            modifier            = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PREVIEW
// ─────────────────────────────────────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PreviewActiveTracking() {
    AutoTripTheme { ActiveTrackingScreen(rememberNavController()) }
}