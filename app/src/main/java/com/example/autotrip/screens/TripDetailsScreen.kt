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
import kotlinx.coroutines.delay
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

    val allTrips by tripsViewModel.trips.collectAsState()
    var trip     by remember { mutableStateOf<Trip?>(null) }

    LaunchedEffect(tripId, allTrips) {
        trip = allTrips.find { it.id == tripId }
        // Firestore may not have loaded yet — wait briefly then show skeleton
        if (trip == null) {
            delay(200)
            if (trip == null) {
                trip = Trip(
                    id          = tripId,
                    origin      = "",
                    destination = "",
                    startTime   = "—",
                    endTime     = "—",
                    travelMode  = "",
                    purpose     = "",
                    companions  = 0,
                    cost        = 0.0,
                    status      = "Needs Info",
                    date        = LocalDate.now().toString()
                )
            }
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
                placeholder     = { Text("e.g. Mall, College, Hospital") },
                singleLine      = true,
                leadingIcon     = { Icon(Icons.Default.Flag, null, tint = Color(0xFFD32F2F)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier        = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(22.dp))

            // ── Travel mode ───────────────────────────────────────
            SectionHeader(title = "Travel Mode", icon = Icons.Default.DirectionsCar)
            Spacer(Modifier.height(10.dp))
            ModeSelector(selected = selectedMode, onSelect = { selectedMode = it })

            Spacer(Modifier.height(22.dp))

            // ── Purpose ───────────────────────────────────────────
            SectionHeader(title = "Trip Purpose", icon = Icons.Default.Flag)
            Spacer(Modifier.height(10.dp))
            PurposeSelector(selected = selectedPurpose, onSelect = { selectedPurpose = it })

            Spacer(Modifier.height(22.dp))

            // ── Companions + cost ─────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    SectionHeader(title = "Companions", icon = Icons.Default.People)
                    Spacer(Modifier.height(10.dp))
                    CompanionsStepper(
                        value      = companions,
                        onDecrease = { if (companions > 0) companions-- },
                        onIncrease = { companions++ }
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    SectionHeader(title = "Cost (₹)", icon = Icons.Default.CurrencyRupee)
                    Spacer(Modifier.height(10.dp))
                    CostField(value = costText, onChange = { costText = it })
                }
            }

            Spacer(Modifier.height(32.dp))

            // ── Validation hint ───────────────────────────────────
            if (!canSave) {
                Text(
                    "⚠ Select travel mode and trip purpose to save",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // ── Save Trip ─────────────────────────────────────────
            Button(
                onClick   = {
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

            // ── Discard Trip ──────────────────────────────────────
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
// TRIP ROUTE MAP  —  green START, red END (fixes same-color issue)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TripRouteMap(routePoints: List<String>, modifier: Modifier = Modifier) {
    val context = LocalContext.current

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

            val bounds = org.osmdroid.util.BoundingBox.fromGeoPoints(geoPoints)
            mv.zoomToBoundingBox(bounds, true, 60)
            mv.invalidate()
        },
        modifier = modifier
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// ROUTE HERO CARD
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RouteHeroCard(trip: Trip) {
    val statusColor = if (trip.status == "Auto-logged") Color(0xFF2E7D32) else Color(0xFFFF8F00)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(bottomStart = 0.dp, bottomEnd = 0.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text("Route", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                Surface(shape = CircleShape, color = statusColor.copy(alpha = 0.20f)) {
                    Row(
                        modifier              = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(modifier = Modifier.size(6.dp).background(statusColor, CircleShape))
                        Text(trip.status, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("From", fontSize = 10.sp, color = Color.White.copy(alpha = 0.55f))
                    Spacer(Modifier.height(2.dp))
                    Text(trip.origin.ifBlank { "—" }, color = Color.White,
                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2)
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFF69F0AE), CircleShape))
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null,
                    tint     = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp).padding(top = 16.dp))
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("To", fontSize = 10.sp, color = Color.White.copy(alpha = 0.55f))
                    Spacer(Modifier.height(2.dp))
                    Text(trip.destination.ifBlank { "—" }, color = Color.White,
                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                        maxLines = 2, textAlign = TextAlign.End)
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFFFF6E40), CircleShape))
                }
            }

            Spacer(Modifier.height(20.dp))

            if (trip.distanceKm > 0) {
                Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.12f)) {
                    Text("%.2f km".format(trip.distanceKm),
                        modifier   = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        color      = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(8.dp))
            }

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("${trip.startTime} – ${trip.endTime}",
                    color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                Text(trip.date, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION HEADER
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MODE SELECTOR — 2 rows of 3, no Metro, no external dependency
// ─────────────────────────────────────────────────────────────────────────────

private data class ModeOption(val label: String, val emoji: String)

private val travelModeOptions = listOf(
    ModeOption("Walk",          "🚶"),
    ModeOption("Bike",          "🚲"),
    ModeOption("Auto-Rickshaw", "🛺"),
    ModeOption("Car",           "🚗"),
    ModeOption("Bus",           "🚌"),
    ModeOption("Train",         "🚆")
)

@Composable
private fun ModeSelector(selected: String, onSelect: (String) -> Unit) {
    val rows = travelModeOptions.chunked(3)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { option ->
                    val isSelected = selected == option.label
                    val scale by animateFloatAsState(
                        targetValue   = if (isSelected) 1.06f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label         = "modeScale"
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .scale(scale)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(0.12f)
                                else Color.Transparent
                            )
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(0.28f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelect(option.label) }
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(option.emoji, fontSize = 18.sp)
                        Text(
                            option.label.split("-").first(),
                            style      = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color      = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            maxLines   = 1
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PURPOSE SELECTOR — 2 rows of 4, no external dependency
// ─────────────────────────────────────────────────────────────────────────────

private val purposeOptions = listOf(
    "Work / Office", "Education", "Shopping", "Recreation",
    "Medical", "Personal", "Return Home", "Other"
)

@Composable
private fun PurposeSelector(selected: String, onSelect: (String) -> Unit) {
    val rows = purposeOptions.chunked(4)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { option ->
                    FilterChip(
                        selected  = selected == option,
                        onClick   = { onSelect(option) },
                        label     = { Text(option, fontSize = 11.sp, maxLines = 1) },
                        modifier  = Modifier.weight(1f),
                        colors    = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(0.12f),
                            selectedLabelColor     = MaterialTheme.colorScheme.primary
                        )
                    )
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COMPANIONS STEPPER
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CompanionsStepper(value: Int, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(0.3f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onDecrease, enabled = value > 0) {
            Icon(Icons.Default.Remove, null, tint = MaterialTheme.colorScheme.primary)
        }
        Text("$value", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onIncrease) {
            Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COST FIELD
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CostField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value           = value,
        onValueChange   = onChange,
        placeholder     = { Text("0.00") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine      = true,
        modifier        = Modifier.fillMaxWidth()
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// PREVIEW
// ─────────────────────────────────────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PreviewTripDetails() {
    AutoTripTheme { TripDetailsScreen(rememberNavController(), "preview_id") }
}