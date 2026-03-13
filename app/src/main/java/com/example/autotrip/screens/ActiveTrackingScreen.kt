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

/** Real GPS tracking — launched from Home FAB */
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
 * Simulated GPS tracking — launched from Dev Tools.
 *
 * KEY FIX: This composable creates its OWN ViewModel via viewModel() and
 * calls startSimulation() itself. Previously DevToolsScreen called viewModel()
 * + startSimulation(), then this screen called viewModel() again — different
 * instance on a different back-stack entry — so the simulation ran in a ViewModel
 * nobody was observing. Nothing showed up.
 *
 * Now: DevTools only navigates with route parameters. This screen owns everything.
 */
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
    val context = LocalContext.current

    // Start simulation exactly once when this composable enters composition
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

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val ok = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (ok) { trackingVm.startTracking(context); phase = TrackingPhase.TRACKING }
    }

    val saveState    by trackingVm.saveState.collectAsState()
    val isSimulating by trackingVm.isSimulating.collectAsState()

    LaunchedEffect(saveState) {
        if (saveState is ActiveTrackingViewModel.SaveState.Saved) {
            val tripId = (saveState as ActiveTrackingViewModel.SaveState.Saved).tripId
            trackingVm.resetSaveState()
            navController.navigate("trip_details/$tripId") {
                popUpTo("active_tracking") { inclusive = true }
            }
        }
    }

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
            transitionSpec = { fadeIn(tween(350)) + slideInHorizontally(tween(350)) { it } togetherWith fadeOut(tween(200)) },
            label        = "phaseTransition"
        ) { currentPhase ->
            when (currentPhase) {
                TrackingPhase.INPUT -> InputPhase(
                    padding             = padding,
                    origin              = origin,
                    destination         = destination,
                    onOriginChange      = { origin = it },
                    onDestinationChange = { destination = it },
                    onStartTracking     = {
                        val fine   = Manifest.permission.ACCESS_FINE_LOCATION
                        val coarse = Manifest.permission.ACCESS_COARSE_LOCATION
                        val ok = ContextCompat.checkSelfPermission(context, fine) ==
                                PackageManager.PERMISSION_GRANTED ||
                                ContextCompat.checkSelfPermission(context, coarse) ==
                                PackageManager.PERMISSION_GRANTED
                        if (ok) { trackingVm.startTracking(context); phase = TrackingPhase.TRACKING }
                        else permLauncher.launch(arrayOf(fine, coarse))
                    }
                )
                TrackingPhase.TRACKING -> ActiveTrackingPhase(
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
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value         = origin,
            onValueChange = onOriginChange,
            label         = { Text("Starting point") },
            leadingIcon   = { Icon(Icons.Default.MyLocation, null,
                tint = Color(0xFF2E7D32)) },
            modifier      = Modifier.fillMaxWidth(),
            singleLine    = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value         = destination,
            onValueChange = onDestinationChange,
            label         = { Text("Destination") },
            leadingIcon   = { Icon(Icons.Default.Place, null,
                tint = Color(0xFFD32F2F)) },
            modifier      = Modifier.fillMaxWidth(),
            singleLine    = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
        )

        Spacer(Modifier.height(16.dp))
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ) {
            Row(modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Text("Trips under 100 m won't be recorded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }

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
    val distanceKm  by trackingVm.distanceKm.collectAsState()
    val speedKmh    by trackingVm.speedKmh.collectAsState()
    val routePoints by trackingVm.routePoints.collectAsState()
    val isLoading   by trackingVm.isLoading.collectAsState()

    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); seconds++ } }

    val timeLabel = remember(seconds) {
        val h = seconds / 3600; val m = (seconds % 3600) / 60; val s = seconds % 60
        if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    val pulseAnim  = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseAnim.animateFloat(
        1f, 1.15f,
        infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulseScale"
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(padding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Live map ─────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .clip(RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            LiveOsmMap(routePoints = routePoints, modifier = Modifier.fillMaxSize())

            // Loading overlay while OSRM route is fetching
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(color = Color.White,
                            modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                        Text("Fetching road route…",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // ── Pulse icon ────────────────────────────────────────────
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
            Box(modifier = Modifier
                .size((80 * pulseScale).dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape))
            Box(modifier = Modifier.size(64.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape))
            Box(modifier = Modifier.size(44.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center) {
                Icon(
                    if (isSimulating) Icons.Default.BugReport else Icons.Default.MyLocation,
                    null,
                    tint     = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
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

        // ── Route card ────────────────────────────────────────────
        Surface(
            modifier       = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape          = RoundedCornerShape(16.dp),
            color          = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp, shadowElevation = 3.dp
        ) {
            Column(modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RouteRow("From", origin.ifBlank { "Current Location" }, Color(0xFF2E7D32))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                RouteRow("To",   destination.ifBlank { "Unknown" },     Color(0xFFD32F2F))
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Stats row ─────────────────────────────────────────────
        Row(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard("Time",     timeLabel,                     Icons.Default.Timer,  Modifier.weight(1f))
            StatCard("Distance", "%.2f km".format(distanceKm),  Icons.Default.Route,  Modifier.weight(1f))
            StatCard("Speed",    "%.1f km/h".format(speedKmh),  Icons.Default.Speed,  Modifier.weight(1f))
        }

        Spacer(Modifier.weight(1f))

        // ── Stop button ───────────────────────────────────────────
        val isSaving = saveState is ActiveTrackingViewModel.SaveState.Saving
        Button(
            onClick   = { onEndTrip(seconds) },
            enabled   = !isSaving,
            modifier  = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(56.dp),
            shape     = RoundedCornerShape(16.dp),
            colors    = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            if (isSaving) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Saving…", color = MaterialTheme.colorScheme.onError, fontWeight = FontWeight.SemiBold)
            } else {
                Icon(Icons.Default.Stop, null,
                    tint = MaterialTheme.colorScheme.onError, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("End Trip", color = MaterialTheme.colorScheme.onError,
                    fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LIVE OSM MAP
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LiveOsmMap(
    routePoints : List<Pair<Double, Double>>,
    modifier    : Modifier = Modifier
) {
    val context = LocalContext.current

    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            // Start centered on Aurangabad at a sensible zoom so tiles load immediately
            controller.setZoom(14.0)
            controller.setCenter(GeoPoint(19.8762, 75.3433))
        }
    }

    val polyline = remember { Polyline().apply { outlinePaint.strokeWidth = 10f } }
    val marker   = remember { Marker(mapView) }

    LaunchedEffect(routePoints) {
        if (routePoints.isEmpty()) return@LaunchedEffect

        val geoPoints = routePoints.map { (lat, lng) -> GeoPoint(lat, lng) }

        polyline.setPoints(geoPoints)
        polyline.outlinePaint.color = android.graphics.Color.parseColor("#1565C0")

        val last = geoPoints.last()
        marker.position = last
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.title = "Current Position"

        if (!mapView.overlays.contains(polyline)) mapView.overlays.add(polyline)
        if (!mapView.overlays.contains(marker))   mapView.overlays.add(marker)

        // On first fix: zoom to origin area; thereafter just follow the marker
        if (routePoints.size == 1) {
            mapView.controller.setZoom(16.0)
        }
        mapView.controller.animateTo(last)
        mapView.invalidate()
    }

    AndroidView(factory = { mapView }, modifier = modifier)

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose { mapView.onPause() }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HELPERS
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RouteRow(label: String, value: String, dotColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(modifier = Modifier.size(10.dp).background(dotColor, CircleShape))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun StatCard(
    label    : String,
    value    : String,
    icon     : ImageVector,
    modifier : Modifier = Modifier
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp, shadowElevation = 2.dp) {
        Column(modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp))
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PreviewActiveTrackingScreen() {
    AutoTripTheme { ActiveTrackingScreen(rememberNavController()) }
}