package com.example.autotrip.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.autotrip.components.AutoTripTopBar
import com.example.autotrip.model.Trip
import com.example.autotrip.ui.theme.AutoTripTheme
import com.example.autotrip.viewmodel.AuthViewModel
import com.example.autotrip.viewmodel.TripSaveState
import com.example.autotrip.viewmodel.TripsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.time.LocalDate

// ─────────────────────────────────────────────────────────────────────────────
// SCREEN
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailsScreen(
    navController  : NavController,
    tripId         : String,
    authViewModel  : AuthViewModel?  = null,
    tripsViewModel : TripsViewModel  = viewModel()
) {
    val saveState    by tripsViewModel.saveState.collectAsState()
    val snackbarHost  = remember { SnackbarHostState() }

    // Navigate home after a successful save
    LaunchedEffect(saveState) {
        when (saveState) {
            is TripSaveState.Saved -> {
                tripsViewModel.resetSaveState()
                navController.navigate("home") {
                    popUpTo("home") { inclusive = false }
                }
            }
            is TripSaveState.Error -> {
                snackbarHost.showSnackbar((saveState as TripSaveState.Error).message)
                tripsViewModel.resetSaveState()
            }
            else -> {}
        }
    }

    // ── Load trip: first try the in-memory flow, then fall back to direct Firestore fetch ──
    // This avoids the race where the flow hasn't loaded yet when navigating from End Trip.
    val allTrips by tripsViewModel.trips.collectAsState()
    var trip     by remember { mutableStateOf<Trip?>(null) }

    LaunchedEffect(tripId, allTrips) {
        // Try the already-loaded list first (instant)
        val found = allTrips.find { it.id == tripId }
        if (found != null) {
            trip = found
            return@LaunchedEffect
        }
        // Not in list yet — fetch directly from Firestore on IO thread (won't block UI)
        val fetched = withContext(Dispatchers.IO) {
            tripsViewModel.fetchTripById(tripId)
        }
        if (fetched != null) {
            trip = fetched
        } else {
            // Last resort skeleton so screen doesn't stay blank
            trip = Trip(
                id          = tripId,
                origin      = "",
                destination = "",
                startTime   = "—",
                endTime     = "—",
                status      = "Needs Info",
                date        = LocalDate.now().toString()
            )
        }
    }

    Scaffold(
        topBar = {
            AutoTripTopBar(
                navController = navController,
                currentRoute  = "trip_details/$tripId",
                title         = "Trip Details",
                authViewModel = authViewModel
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { padding ->
        AnimatedContent(
            targetState    = trip,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
            label          = "tripContent"
        ) { currentTrip ->
            if (currentTrip == null) {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                TripDetailsContent(
                    trip      = currentTrip,
                    padding   = padding,
                    isSaving  = saveState is TripSaveState.Saving,
                    onSave    = { updates -> tripsViewModel.updateTrip(currentTrip.id, updates) },
                    onDiscard = { navController.popBackStack() }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CONTENT
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TripDetailsContent(
    trip      : Trip,
    padding   : PaddingValues,
    isSaving  : Boolean,
    onSave    : (Map<String, Any>) -> Unit,
    onDiscard : () -> Unit
) {
    var selectedMode    by remember(trip) { mutableStateOf(trip.travelMode) }
    var selectedPurpose by remember(trip) { mutableStateOf(trip.purpose) }
    var companions      by remember(trip) { mutableStateOf(trip.companions) }
    var costText        by remember(trip) { mutableStateOf(if (trip.cost > 0) trip.cost.toString() else "") }
    var originName      by remember(trip) { mutableStateOf(trip.origin) }
    var destName        by remember(trip) { mutableStateOf(trip.destination) }

    // Mode AND purpose must be filled; companions and cost are optional
    val canSave = selectedMode.isNotBlank() && selectedPurpose.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
    ) {
        // ── OSM route replay map ──────────────────────────────────
        if (trip.routePoints.isNotEmpty()) {
            TripRouteMap(
                routePoints = trip.routePoints,
                modifier    = Modifier.fillMaxWidth().height(220.dp)
            )
        }

        RouteHeroCard(trip = trip)

        Spacer(Modifier.height(24.dp))

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {

            // ── Editable place names ──────────────────────────────
            SectionHeader(title = "Start Point Name", icon = Icons.Default.LocationOn)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value           = originName,
                onValueChange   = { originName = it },
                placeholder     = { Text("e.g. Home, Station, Office") },
                singleLine      = true,
                leadingIcon     = { Icon(Icons.Default.LocationOn, null, tint = Color(0xFF2E7D32)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier        = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            SectionHeader(title = "End Point Name", icon = Icons.Default.Flag)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value           = destName,
                onValueChange   = { destName = it },
                placeholder     = { Text("e.g. Office, Mall, Hospital") },
                singleLine      = true,
                leadingIcon     = { Icon(Icons.Default.Flag, null, tint = Color(0xFFD32F2F)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier        = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(24.dp))

            // ── Travel Mode ───────────────────────────────────────
            SectionHeader(title = "Travel Mode", icon = Icons.Default.DirectionsCar)
            Spacer(Modifier.height(10.dp))
            TravelModeGrid(selected = selectedMode, onSelect = { selectedMode = it })

            Spacer(Modifier.height(24.dp))

            // ── Purpose ───────────────────────────────────────────
            SectionHeader(title = "Trip Purpose", icon = Icons.Default.Info)
            Spacer(Modifier.height(10.dp))
            PurposeGrid(selected = selectedPurpose, onSelect = { selectedPurpose = it })

            Spacer(Modifier.height(24.dp))

            // ── Companions ────────────────────────────────────────
            SectionHeader(title = "Companions (optional)", icon = Icons.Default.Group)
            Spacer(Modifier.height(10.dp))
            CompanionsRow(count = companions, onChange = { companions = it })

            Spacer(Modifier.height(24.dp))

            // ── Cost ──────────────────────────────────────────────
            SectionHeader(title = "Cost (optional)", icon = Icons.Default.CurrencyRupee)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value           = costText,
                onValueChange   = { costText = it.filter { c -> c.isDigit() || c == '.' } },
                placeholder     = { Text("e.g. 45.50") },
                singleLine      = true,
                leadingIcon     = { Icon(Icons.Default.CurrencyRupee, null) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction    = ImeAction.Done
                ),
                modifier        = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(32.dp))

            // ── Save button ───────────────────────────────────────
            Button(
                onClick = {
                    onSave(mapOf(
                        "origin"      to originName,
                        "destination" to destName,
                        "travelMode"  to selectedMode,
                        "purpose"     to selectedPurpose,
                        "companions"  to companions,
                        "cost"        to (costText.toDoubleOrNull() ?: 0.0),
                        "status"      to "Auto-logged"
                    ))
                },
                enabled   = canSave && !isSaving,
                modifier  = Modifier.fillMaxWidth().height(54.dp),
                shape     = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                } else {
                    Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save Trip", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Discard button ────────────────────────────────────
            OutlinedButton(
                onClick  = onDiscard,
                enabled  = !isSaving,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape    = RoundedCornerShape(16.dp),
                colors   = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.DeleteOutline, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Discard", fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TRIP ROUTE MAP — deferred rendering to prevent ANR
// zoomToBoundingBox is posted to the View's handler so it NEVER blocks the
// main thread during initial composition (which was causing the freeze).
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TripRouteMap(routePoints: List<String>, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // Parse points on the calling thread (already IO-side by the time we get here)
    val geoPoints = remember(routePoints) {
        routePoints.mapNotNull { entry ->
            val parts = entry.split(",")
            if (parts.size == 2) {
                val lat = parts[0].toDoubleOrNull()
                val lng = parts[1].toDoubleOrNull()
                if (lat != null && lng != null) GeoPoint(lat, lng) else null
            } else null
        }
    }

    if (geoPoints.isEmpty()) return

    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isClickable = false
        }
    }

    AndroidView(
        factory = { mapView },
        update  = { mv ->
            mv.overlays.clear()

            mv.overlays.add(Polyline().apply {
                setPoints(geoPoints)
                outlinePaint.color       = android.graphics.Color.parseColor("#1565C0")
                outlinePaint.strokeWidth = 8f
            })

            // Green start marker
            mv.overlays.add(Marker(mv).apply {
                position = geoPoints.first()
                title    = "Start"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = context.getDrawable(org.osmdroid.library.R.drawable.marker_default)
                    ?.apply { setTint(android.graphics.Color.parseColor("#2E7D32")) }
            })

            // Red end marker
            mv.overlays.add(Marker(mv).apply {
                position = geoPoints.last()
                title    = "End"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = context.getDrawable(org.osmdroid.library.R.drawable.marker_default)
                    ?.apply { setTint(android.graphics.Color.parseColor("#D32F2F")) }
            })

            // KEY FIX: post zoomToBoundingBox so it runs AFTER the view is laid out.
            // Calling it synchronously here was blocking the main thread → ANR freeze.
            mv.post {
                try {
                    val bounds = org.osmdroid.util.BoundingBox.fromGeoPoints(geoPoints)
                    mv.zoomToBoundingBox(bounds, false, 80)
                    mv.invalidate()
                } catch (_: Exception) {
                    // Fallback if bounds calc fails (e.g. single point)
                    mv.controller.setZoom(14.0)
                    mv.controller.setCenter(geoPoints.first())
                    mv.invalidate()
                }
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
// ROUTE HERO CARD
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RouteHeroCard(trip: Trip) {
    val statusColor = if (trip.status == "Auto-logged") Color(0xFF2E7D32) else Color(0xFFF57F17)

    Surface(
        modifier       = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        shape          = RoundedCornerShape(20.dp),
        tonalElevation = 3.dp,
        shadowElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // Status badge
            Surface(
                shape = RoundedCornerShape(50.dp),
                color = statusColor.copy(alpha = 0.12f)
            ) {
                Text(
                    text     = trip.status.ifBlank { "Needs Info" },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    color    = statusColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Route row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("From", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    Text(trip.origin.ifBlank { "—" },
                        fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(horizontal = 8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("To", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    Text(trip.destination.ifBlank { "—" },
                        fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            // Stats row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TripStat(label = "Date",     value = trip.date.ifBlank { "—" })
                TripStat(label = "Start",    value = trip.startTime.ifBlank { "—" })
                TripStat(label = "End",      value = trip.endTime.ifBlank { "—" })
                TripStat(label = "Distance", value = if (trip.distanceKm > 0) "%.1f km".format(trip.distanceKm) else "—")
            }
        }
    }
}

@Composable
private fun TripStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TRAVEL MODE GRID
// ─────────────────────────────────────────────────────────────────────────────

private val travelModes = listOf(
    Triple("Car",        Icons.Default.DirectionsCar,               Color(0xFF1565C0)),
    Triple("Bus",        Icons.Default.DirectionsBus,               Color(0xFF6A1B9A)),
    Triple("Auto",       Icons.Default.ElectricRickshaw,            Color(0xFF00838F)),
    Triple("Bike",       Icons.AutoMirrored.Filled.DirectionsBike,  Color(0xFF2E7D32)),
    Triple("Walk",       Icons.AutoMirrored.Filled.DirectionsWalk,  Color(0xFF558B2F)),
    Triple("Other",      Icons.Default.MoreHoriz,                   Color(0xFF4E342E))
)

@Composable
private fun TravelModeGrid(selected: String, onSelect: (String) -> Unit) {
    val rows = travelModes.chunked(4)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, icon, color) ->
                    ModeChip(
                        label    = label,
                        icon     = icon,
                        color    = color,
                        selected = selected == label,
                        onClick  = { onSelect(label) },
                        modifier = Modifier.weight(1f)
                    )
                }
                // Fill empty slots in last row
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ModeChip(
    label    : String,
    icon     : ImageVector,
    color    : Color,
    selected : Boolean,
    onClick  : () -> Unit,
    modifier : Modifier = Modifier
) {
    val bgColor  = if (selected) color.copy(alpha = 0.13f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val border   = if (selected) color.copy(alpha = 0.6f) else Color.Transparent
    val scale    by animateFloatAsState(if (selected) 1.04f else 1f, label = "modeScale")

    Surface(
        shape    = RoundedCornerShape(12.dp),
        color    = bgColor,
        modifier = modifier
            .scale(scale)
            .border(1.5.dp, border, RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Column(
            modifier            = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = label,
                tint     = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp))
            Text(label,
                fontSize   = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color      = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign  = TextAlign.Center)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PURPOSE GRID
// ─────────────────────────────────────────────────────────────────────────────

private val purposes = listOf(
    "Work", "Education", "Shopping", "Medical",
    "Recreation", "Religious", "Social", "Other"
)

@Composable
private fun PurposeGrid(selected: String, onSelect: (String) -> Unit) {
    val rows = purposes.chunked(4)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { label ->
                    PurposeChip(
                        label    = label,
                        selected = selected == label,
                        onClick  = { onSelect(label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PurposeChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color   = MaterialTheme.colorScheme.primary
    val bgColor = if (selected) color.copy(alpha = 0.13f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val border  = if (selected) color.copy(alpha = 0.6f) else Color.Transparent
    val scale   by animateFloatAsState(if (selected) 1.04f else 1f, label = "purposeScale")

    Surface(
        shape    = RoundedCornerShape(10.dp),
        color    = bgColor,
        modifier = modifier
            .scale(scale)
            .border(1.5.dp, border, RoundedCornerShape(10.dp))
            .clickable { onClick() }
    ) {
        Box(Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(
                text       = label,
                fontSize   = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color      = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign  = TextAlign.Center
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COMPANIONS ROW
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CompanionsRow(count: Int, onChange: (Int) -> Unit) {
    Row(
        modifier          = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconButton(
            onClick  = { if (count > 0) onChange(count - 1) },
            enabled  = count > 0,
            modifier = Modifier.size(40.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
        ) {
            Icon(Icons.Default.Remove, null, modifier = Modifier.size(18.dp))
        }
        Text(
            text       = count.toString(),
            modifier   = Modifier.weight(1f),
            textAlign  = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            fontSize   = 20.sp
        )
        IconButton(
            onClick  = { onChange(count + 1) },
            modifier = Modifier.size(40.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
        ) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION HEADER
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PREVIEW
// ─────────────────────────────────────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PreviewTripDetails() {
    AutoTripTheme { TripDetailsScreen(rememberNavController(), "preview_id") }
}