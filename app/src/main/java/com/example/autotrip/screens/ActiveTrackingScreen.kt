package com.example.autotrip.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.autotrip.components.AutoTripTopBar
import com.example.autotrip.location.SimulatedLocationProvider
import com.example.autotrip.prefs.DataSharingPrefs
import com.example.autotrip.simulation.SimMode
import com.example.autotrip.simulation.SimPreset
import com.example.autotrip.ui.theme.AutoTripTheme
import com.example.autotrip.viewmodel.ActiveTrackingViewModel
import com.example.autotrip.viewmodel.AuthViewModel
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlin.coroutines.resume
import androidx.core.graphics.toColorInt

// ─────────────────────────────────────────────────────────────────────────────
// STATE
// ─────────────────────────────────────────────────────────────────────────────

private enum class TrackingPhase { SHARING_BLOCKED, MAP_PICK, TRACKING }

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
    val context    = LocalContext.current
    val trackingVm: ActiveTrackingViewModel = viewModel()

    // Check data sharing before starting simulation too
    val sharingEnabled = remember { DataSharingPrefs.isTripSharingEnabled(context) }

    if (!sharingEnabled) {
        // Show the blocked screen instead of starting simulation
        ActiveTrackingContent(
            navController = navController,
            authViewModel = authViewModel,
            trackingVm    = trackingVm,
            simOrigin     = originName,
            simDest       = destName,
            forceSharingBlocked = true
        )
        return
    }

    LaunchedEffect(Unit) {
        val origin   = SimPreset(originName, originLat, originLng)
        val dest     = SimPreset(destName,   destLat,   destLng)
        val mode     = SimMode.entries.firstOrNull { it.name == modeName } ?: SimMode.CAR
        val provider = SimulatedLocationProvider(origin, dest, mode)
        trackingVm.startSimulation(provider, speedKmh = mode.avgSpeedKmh)
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
    navController       : NavController,
    authViewModel       : AuthViewModel?,
    trackingVm          : ActiveTrackingViewModel,
    simOrigin           : String?,
    simDest             : String?,
    forceSharingBlocked : Boolean = false
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isSimMode      = simOrigin != null

    // Re-check data sharing pref on every composition (handles returning from settings)
    var sharingEnabled by remember {
        mutableStateOf(DataSharingPrefs.isTripSharingEnabled(context))
    }
    // Also refresh when screen resumes (user may have toggled in profile)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                sharingEnabled = DataSharingPrefs.isTripSharingEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Determine initial phase
    val initialPhase = when {
        forceSharingBlocked || !sharingEnabled -> TrackingPhase.SHARING_BLOCKED
        isSimMode                              -> TrackingPhase.TRACKING
        else                                   -> TrackingPhase.MAP_PICK
    }

    var phase       by remember { mutableStateOf(initialPhase) }
    var origin      by remember { mutableStateOf(simOrigin ?: "") }
    var destination by remember { mutableStateOf(simDest ?: "") }

    val saveState    by trackingVm.saveState.collectAsState()
    val isSimulating by trackingVm.isSimulating.collectAsState()
    val isActive     by trackingVm.isTrackingActive.collectAsState()

    // Update phase when sharing pref changes
    LaunchedEffect(sharingEnabled) {
        if (!sharingEnabled && phase != TrackingPhase.TRACKING) {
            phase = TrackingPhase.SHARING_BLOCKED
        } else if (sharingEnabled && phase == TrackingPhase.SHARING_BLOCKED) {
            phase = if (isSimMode) TrackingPhase.TRACKING else TrackingPhase.MAP_PICK
        }
    }

    // If user returns to this screen and tracking is already running (came back from bg),
    // skip straight to the TRACKING phase
    LaunchedEffect(isActive) {
        if (isActive && phase == TrackingPhase.MAP_PICK) {
            phase = TrackingPhase.TRACKING
        }
    }

    // Bind/unbind service as the screen goes in and out of foreground
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    if (!isSimMode && phase == TrackingPhase.TRACKING) {
                        trackingVm.bindToRunningService(context)
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    if (!isSimMode) {
                        trackingVm.unbindFromService(context)
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Navigate to trip details once saved
    LaunchedEffect(saveState) {
        if (saveState is ActiveTrackingViewModel.SaveState.Saved) {
            val tripId = (saveState as ActiveTrackingViewModel.SaveState.Saved).tripId
            navController.navigate("trip_details/$tripId") {
                popUpTo("active_tracking") { inclusive = true }
            }
        }
    }

    val fine   = Manifest.permission.ACCESS_FINE_LOCATION
    val coarse = Manifest.permission.ACCESS_COARSE_LOCATION
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[fine] == true || grants[coarse] == true) {
            trackingVm.startTracking(context, origin, destination)
            phase = TrackingPhase.TRACKING
        }
    }

    Scaffold(
        topBar = {
            AutoTripTopBar(
                navController = navController,
                currentRoute  = "active_tracking",
                title         = when (phase) {
                    TrackingPhase.SHARING_BLOCKED -> "Trip Recording Paused"
                    TrackingPhase.MAP_PICK        -> "New Trip"
                    TrackingPhase.TRACKING        -> if (isSimMode) "Simulation" else "Live Tracking"
                },
                authViewModel = authViewModel
            )
        }
    ) { padding ->
        when (phase) {

            // ── Sharing is OFF — show gate screen ─────────────────
            TrackingPhase.SHARING_BLOCKED -> SharingBlockedScreen(
                padding  = padding,
                onGoBack = { navController.popBackStack() }
            )

            // ── Map picker ────────────────────────────────────────
            TrackingPhase.MAP_PICK -> MapPickPhase(
                padding         = padding,
                onDiscard       = { navController.popBackStack() },
                onStartTracking = { originName, destName ->
                    origin      = originName
                    destination = destName
                    val hasFine = ContextCompat.checkSelfPermission(context, fine) ==
                            PackageManager.PERMISSION_GRANTED
                    if (hasFine) {
                        trackingVm.startTracking(context, originName, destName)
                        phase = TrackingPhase.TRACKING
                    } else {
                        permLauncher.launch(arrayOf(fine, coarse))
                    }
                }
            )

            // ── Active tracking ───────────────────────────────────
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
// SHARING BLOCKED SCREEN
// Shown when the user has turned off "Share Anonymous Trip Data" in settings.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SharingBlockedScreen(
    padding        : PaddingValues,
    onGoBack       : () -> Unit
) {
    Box(
        modifier         = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Block,
                    contentDescription = null,
                    tint     = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(44.dp)
                )
            }

            Text(
                "Trip Recording is Paused",
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center
            )

            Text(
                "You have disabled anonymous trip data sharing. " +
                        "Trip recording is paused until you turn it back on.",
                style     = MaterialTheme.typography.bodyMedium,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // Info card
            Surface(
                shape          = RoundedCornerShape(14.dp),
                color          = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier       = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment     = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint     = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp).padding(top = 1.dp)
                        )
                        Text(
                            "To re-enable trip recording, go to Profile → Settings → Privacy & Data " +
                                    "and turn on \"Share Anonymous Trip Data\".",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Row(
                        verticalAlignment     = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = null,
                            tint     = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp).padding(top = 1.dp)
                        )
                        Text(
                            "Your personal profile information (name, demographics, etc.) " +
                                    "is not affected by this setting.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Go back button
            OutlinedButton(
                onClick  = onGoBack,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape    = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Go Back")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PHASE 1 — MAP PICKER
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MapPickPhase(
    padding         : PaddingValues,
    onDiscard       : () -> Unit,
    onStartTracking : (originName: String, destName: String) -> Unit
) {
    val context = LocalContext.current

    var currentLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var destPoint       by remember { mutableStateOf<GeoPoint?>(null) }
    var originLabel     by remember { mutableStateOf("My Location") }
    var destLabel       by remember { mutableStateOf("") }
    var fetchingLoc     by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val loc = suspendCancellableCoroutine<android.location.Location?> { cont ->
                try {
                    fusedClient.lastLocation
                        .addOnSuccessListener { cont.resume(it) }
                        .addOnFailureListener { cont.resume(null) }
                } catch (e: SecurityException) { cont.resume(null) }
            }
            if (loc != null) currentLocation = GeoPoint(loc.latitude, loc.longitude)
        } catch (_: Exception) { }
        fetchingLoc = false
    }

    Column(modifier = Modifier.fillMaxSize().padding(padding)) {

        PickerBanner(currentLocation = currentLocation, destPoint = destPoint, fetchingLoc = fetchingLoc)

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            LivePickerMapView(
                currentLocation = currentLocation,
                destPoint       = destPoint,
                onTap           = { tapped ->
                    destPoint = tapped
                    if (destLabel.isBlank())
                        destLabel = "Destination %.4f,%.4f".format(tapped.latitude, tapped.longitude)
                },
                modifier = Modifier.fillMaxSize()
            )

            if (destPoint == null) {
                Icon(
                    Icons.Default.AddCircleOutline,
                    contentDescription = "Tap to set destination",
                    modifier = Modifier.align(Alignment.Center).size(36.dp),
                    tint = Color(0xFFD32F2F).copy(alpha = 0.75f)
                )
            }

            if (destPoint != null) {
                SmallFloatingActionButton(
                    onClick        = { destPoint = null; destLabel = "" },
                    modifier       = Modifier.align(Alignment.TopEnd).padding(10.dp),
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor   = MaterialTheme.colorScheme.onErrorContainer
                ) {
                    Icon(Icons.Default.Refresh, "Reset destination", modifier = Modifier.size(18.dp))
                }
            }
        }

        Surface(tonalElevation = 4.dp, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value         = originLabel,
                    onValueChange = { originLabel = it },
                    label         = { Text("Start point name") },
                    leadingIcon   = {
                        Icon(Icons.Default.MyLocation, null,
                            tint = Color(0xFF1565C0), modifier = Modifier.size(20.dp))
                    },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value         = destLabel,
                    onValueChange = { destLabel = it },
                    label         = { Text("Destination name") },
                    placeholder   = { Text("Tap map to place pin first") },
                    leadingIcon   = {
                        Icon(Icons.Default.Flag, null,
                            tint = Color(0xFFD32F2F), modifier = Modifier.size(20.dp))
                    },
                    enabled    = destPoint != null,
                    singleLine = true,
                    modifier   = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick  = onDiscard,
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape    = RoundedCornerShape(14.dp),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Discard", fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick   = {
                            val oName = originLabel.ifBlank { "My Location" }
                            val dName = destLabel.ifBlank {
                                destPoint?.let { "Dest %.4f,%.4f".format(it.latitude, it.longitude) } ?: "Destination"
                            }
                            onStartTracking(oName, dName)
                        },
                        enabled   = destPoint != null,
                        modifier  = Modifier.weight(2f).height(50.dp),
                        shape     = RoundedCornerShape(14.dp),
                        colors    = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) {
                        Icon(Icons.Default.GpsFixed, null,
                            tint     = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Start Tracking",
                            color      = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PICKER BANNER
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PickerBanner(currentLocation: GeoPoint?, destPoint: GeoPoint?, fetchingLoc: Boolean) {
    data class BD(val bg: Color, val tint: Color, val msg: String, val icon: ImageVector)
    val d = when {
        fetchingLoc             -> BD(Color(0xFF1565C0).copy(.08f), Color(0xFF1565C0), "Getting your current location…",                     Icons.Default.GpsFixed)
        currentLocation == null -> BD(Color(0xFFFF6F00).copy(.08f), Color(0xFFFF6F00), "Location unavailable — start auto-set when tracking begins", Icons.Default.LocationOff)
        destPoint == null       -> BD(Color(0xFFD32F2F).copy(.07f), Color(0xFFD32F2F), "Tap anywhere on the map to set your destination",   Icons.Default.TouchApp)
        else                    -> BD(Color(0xFF2E7D32).copy(.08f), Color(0xFF2E7D32), "Route ready — name your places and start tracking", Icons.Default.Check)
    }
    Surface(color = d.bg, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StepDot(1, currentLocation != null && !fetchingLoc, fetchingLoc || (currentLocation == null && !fetchingLoc), Color(0xFF1565C0))
            StepDot(2, destPoint != null, currentLocation != null && destPoint == null, Color(0xFFD32F2F))
            Spacer(Modifier.width(2.dp))
            Icon(d.icon, null, modifier = Modifier.size(18.dp), tint = d.tint)
            Text(d.msg, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun StepDot(number: Int, done: Boolean, active: Boolean, color: Color) {
    val bg     = when { done -> color; active -> color.copy(alpha = 0.18f); else -> Color.Gray.copy(0.13f) }
    val border = if (active || done) color else Color.Gray.copy(alpha = 0.25f)
    Box(
        modifier = Modifier.size(22.dp).background(bg, CircleShape).border(1.5.dp, border, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (done) Icon(Icons.Default.Check, null, modifier = Modifier.size(12.dp), tint = Color.White)
        else Text("$number", style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold, color = if (active) color else Color.Gray)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MAP PICKER VIEW
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LivePickerMapView(
    currentLocation : GeoPoint?,
    destPoint       : GeoPoint?,
    onTap           : (GeoPoint) -> Unit,
    modifier        : Modifier = Modifier
) {
    val context = LocalContext.current
    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(15.0)
            controller.setCenter(GeoPoint(19.8762, 75.3433))
        }
    }
    val currentMarker = remember { Marker(mapView) }
    val destMarker    = remember { Marker(mapView) }
    val routeLine     = remember {
        Polyline().apply {
            outlinePaint.color       = android.graphics.Color.parseColor("#1565C0")
            outlinePaint.strokeWidth = 5f
            outlinePaint.pathEffect  = android.graphics.DashPathEffect(floatArrayOf(20f, 12f), 0f)
        }
    }
    LaunchedEffect(currentLocation, destPoint) {
        mapView.overlays.clear()
        val receiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean { onTap(p); return true }
            override fun longPressHelper(p: GeoPoint): Boolean = false
        }
        mapView.overlays.add(MapEventsOverlay(receiver))
        currentLocation?.let { pt ->
            currentMarker.apply {
                position = pt; title = "You are here"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = context.getDrawable(org.osmdroid.library.R.drawable.marker_default)
                    ?.apply { setTint(android.graphics.Color.parseColor("#1565C0")) }
            }
            mapView.overlays.add(currentMarker)
            mapView.controller.animateTo(pt)
        }
        destPoint?.let { pt ->
            destMarker.apply {
                position = pt; title = "Destination"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = context.getDrawable(org.osmdroid.library.R.drawable.marker_default)
                    ?.apply { setTint(android.graphics.Color.parseColor("#D32F2F")) }
            }
            mapView.overlays.add(destMarker)
        }
        if (currentLocation != null && destPoint != null) {
            routeLine.setPoints(listOf(currentLocation, destPoint))
            mapView.overlays.add(routeLine)
        }
        mapView.invalidate()
    }
    AndroidView(factory = { mapView }, modifier = modifier)
    DisposableEffect(Unit) { mapView.onResume(); onDispose { mapView.onPause() } }
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
    val isActive    by trackingVm.isTrackingActive.collectAsState()

    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); seconds++ } }

    val displaySeconds = if (isSimulating) seconds * 10 else seconds
    val timeLabel = remember(displaySeconds) {
        val h = displaySeconds / 3600; val m = (displaySeconds % 3600) / 60; val s = displaySeconds % 60
        if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    Column(
        modifier            = Modifier.fillMaxSize().padding(padding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))

        LiveRouteMap(
            routePoints = routePoints,
            modifier    = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
        )
        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier.size(56.dp).clip(CircleShape)
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
            if (isSimulating) "Simulated GPS at 10× speed"
            else if (!isSimulating && !isActive) "Reconnecting to service…"
            else "GPS is recording your route • runs in background",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )

        Spacer(Modifier.height(20.dp))

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

        Row(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard("Distance",  "%.2f km".format(distanceKm), Icons.Default.Straighten,  Modifier.weight(1f))
            StatCard("Speed",     "%.1f km/h".format(speedKmh), Icons.Default.Speed,        Modifier.weight(1f))
            StatCard("Time",      timeLabel,                     Icons.Default.AccessTime,   Modifier.weight(1f))
        }

        Spacer(Modifier.weight(1f))

        if (isLoading) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("Acquiring GPS signal…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
        }

        Button(
            onClick   = { onEndTrip(seconds) },
            enabled   = saveState !is ActiveTrackingViewModel.SaveState.Saving,
            modifier  = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(56.dp),
            shape     = RoundedCornerShape(16.dp),
            colors    = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            if (saveState is ActiveTrackingViewModel.SaveState.Saving) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
            } else {
                Icon(Icons.Default.Stop, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("End Trip", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick  = onDiscard,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp),
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
// HELPERS
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RouteRow(label: String, name: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape).border(2.dp, color.copy(alpha = 0.3f), CircleShape))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
            Text(name,  style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier, shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp, shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LIVE ROUTE MAP (during tracking)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LiveRouteMap(routePoints: List<Pair<Double, Double>>, modifier: Modifier = Modifier) {
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
                mv.overlays.add(Polyline().apply {
                    setPoints(geo)
                    outlinePaint.color = android.graphics.Color.parseColor("#1565C0")
                    outlinePaint.strokeWidth = 8f
                })
                mv.overlays.add(Marker(mv).apply {
                    position = geo.first(); title = "Start"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    icon = context.getDrawable(org.osmdroid.library.R.drawable.marker_default)
                        ?.apply { setTint("#2E7D32".toColorInt()) }
                })
                mv.overlays.add(Marker(mv).apply {
                    position = geo.last(); title = "You"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    icon = context.getDrawable(org.osmdroid.library.R.drawable.marker_default)
                        ?.apply { setTint(android.graphics.Color.parseColor("#1565C0")) }
                })
                mv.controller.animateTo(geo.last())
                mv.controller.setZoom(15.0)
                mv.invalidate()
            }
        },
        modifier = modifier
    )
    DisposableEffect(Unit) { mapView.onResume(); onDispose { mapView.onPause() } }
}

// ─────────────────────────────────────────────────────────────────────────────
// PREVIEW
// ─────────────────────────────────────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PreviewActiveTrackingScreen() {
    AutoTripTheme { ActiveTrackingScreen(rememberNavController()) }
}