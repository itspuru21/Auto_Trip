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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import org.osmdroid.util.BoundingBox
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
    authViewModel  : AuthViewModel? = null,
    tripsViewModel : TripsViewModel  = viewModel()
) {
    val saveState by tripsViewModel.saveState.collectAsState()
    val snackbarHost = remember { SnackbarHostState() }

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
    var trip by remember { mutableStateOf<Trip?>(null) }

    LaunchedEffect(tripId, allTrips) {
        trip = allTrips.find { it.id == tripId }
        if (trip == null) {
            delay(200)
            trip = Trip(
                id          = tripId,
                origin      = "Origin",
                destination = "Destination",
                startTime   = "—",
                endTime     = "—",
                travelMode  = "Car",
                purpose     = "",
                companions  = 0,
                cost        = 0.0,
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
            targetState  = trip,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
            label        = "tripContent"
        ) { currentTrip ->
            if (currentTrip == null) {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                TripDetailsContent(
                    trip     = currentTrip,
                    padding  = padding,
                    isSaving = saveState is TripSaveState.Saving,
                    onSave   = { updates -> tripsViewModel.updateTrip(currentTrip.id, updates) }
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
    trip     : Trip,
    padding  : PaddingValues,
    isSaving : Boolean,
    onSave   : (Map<String, Any>) -> Unit
) {
    var selectedMode    by remember(trip) { mutableStateOf(trip.travelMode) }
    var selectedPurpose by remember(trip) { mutableStateOf(trip.purpose) }
    var companions      by remember(trip) { mutableStateOf(trip.companions) }
    var costText        by remember(trip) { mutableStateOf(if (trip.cost > 0) trip.cost.toString() else "") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
    ) {
        // ── OSM Route Map (top of screen) ────────────────────────
        if (trip.routePoints.isNotEmpty()) {
            TripRouteMap(
                routePoints = trip.routePoints,
                modifier    = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )
        }

        RouteHeroCard(trip = trip)

        Spacer(Modifier.height(24.dp))

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {

            // ── Travel mode ──────────────────────────────────────────
            SectionHeader(title = "Travel Mode", icon = Icons.Default.DirectionsCar)
            Spacer(Modifier.height(10.dp))
            ModeSelector(selected = selectedMode, onSelect = { selectedMode = it })

            Spacer(Modifier.height(22.dp))

            // ── Purpose ──────────────────────────────────────────────
            SectionHeader(title = "Trip Purpose", icon = Icons.Default.Flag)
            Spacer(Modifier.height(10.dp))
            PurposeSelector(selected = selectedPurpose, onSelect = { selectedPurpose = it })

            Spacer(Modifier.height(22.dp))

            // ── Companions + cost ─────────────────────────────────────
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

            SaveButton(
                isSaving = isSaving,
                onClick  = {
                    onSave(mapOf(
                        "travelMode"  to selectedMode,
                        "purpose"     to selectedPurpose,
                        "companions"  to companions,
                        "cost"        to (costText.toDoubleOrNull() ?: 0.0),
                        "status"      to "Auto-logged"
                    ))
                }
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TRIP ROUTE MAP (OSMDroid — static replay of recorded route)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TripRouteMap(
    routePoints : List<String>,
    modifier    : Modifier = Modifier
) {
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
            // Disable user scrolling on the static detail map
            isClickable = false
        }
    }

    AndroidView(
        factory = { mapView },
        update  = { mv ->
            mv.overlays.clear()

            // Draw the full route polyline
            val polyline = Polyline().apply {
                setPoints(geoPoints)
                outlinePaint.color       = android.graphics.Color.parseColor("#1565C0")
                outlinePaint.strokeWidth = 8f
            }
            mv.overlays.add(polyline)

            // Start marker — green
            val startMarker = Marker(mv).apply {
                position  = geoPoints.first()
                title     = "Start"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            mv.overlays.add(startMarker)

            // End marker — red
            val endMarker = Marker(mv).apply {
                position  = geoPoints.last()
                title     = "End"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            mv.overlays.add(endMarker)

            // Zoom map to fit the entire route
            if (geoPoints.size > 1) {
                val box = BoundingBox.fromGeoPoints(geoPoints)
                mv.post {
                    mv.zoomToBoundingBox(box.increaseByScale(1.3f), false)
                    mv.invalidate()
                }
            } else {
                mv.controller.setZoom(15.0)
                mv.controller.setCenter(geoPoints.first())
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
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val alpha by animateFloatAsState(
        targetValue   = if (visible) 1f else 0f,
        animationSpec = tween(400), label = "heroAlpha"
    )
    val offsetY by animateDpAsState(
        targetValue   = if (visible) 0.dp else (-16).dp,
        animationSpec = tween(400, easing = FastOutSlowInEasing), label = "heroOffset"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = offsetY)
            .alpha(alpha),
        color = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
    ) {
        Column(
            modifier            = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status badge
            val (statusColor, statusBg) = when (trip.status) {
                "Auto-logged" -> Pair(Color(0xFF69F0AE), Color(0xFF69F0AE).copy(alpha = 0.18f))
                "Needs Info"  -> Pair(Color(0xFFFFD740), Color(0xFFFFD740).copy(alpha = 0.18f))
                else          -> Pair(Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0.12f))
            }
            Surface(shape = RoundedCornerShape(20.dp), color = statusBg) {
                Row(
                    modifier              = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(modifier = Modifier.size(6.dp).background(statusColor, CircleShape))
                    Text(trip.status, color = statusColor,
                        fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Origin → Destination
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("From", fontSize = 10.sp, color = Color.White.copy(alpha = 0.55f))
                    Spacer(Modifier.height(2.dp))
                    Text(trip.origin.ifBlank { "—" },
                        color = Color.White, fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp, maxLines = 2)
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFF69F0AE), CircleShape))
                }
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward, null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp).padding(top = 16.dp))
                Column(
                    modifier            = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text("To", fontSize = 10.sp, color = Color.White.copy(alpha = 0.55f))
                    Spacer(Modifier.height(2.dp))
                    Text(trip.destination.ifBlank { "—" },
                        color = Color.White, fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp, maxLines = 2, textAlign = TextAlign.End)
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFFFF6E40), CircleShape))
                }
            }

            Spacer(Modifier.height(20.dp))

            // Distance chip (shown if > 0)
            if (trip.distanceKm > 0.0) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier              = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Route, null,
                            tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(14.dp))
                        Text("%.2f km".format(trip.distanceKm),
                            color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
            Spacer(Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TripInfoChip(label = "Departed",  value = trip.startTime)
                VerticalDividerLine()
                TripInfoChip(label = "Arrived",   value = trip.endTime)
                VerticalDividerLine()
                TripInfoChip(label = "Trip ID",   value = "#${trip.id.takeLast(6).uppercase()}")
            }
        }
    }
}

@Composable
private fun VerticalDividerLine() {
    Box(modifier = Modifier.width(1.dp).height(32.dp).background(Color.White.copy(alpha = 0.18f)))
}

@Composable
private fun TripInfoChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, color = Color.White.copy(alpha = 0.55f))
        Spacer(Modifier.height(3.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION HEADER
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MODE SELECTOR
// ─────────────────────────────────────────────────────────────────────────────

private data class ModeOption(val label: String, val icon: ImageVector)

private val travelModeOptions = listOf(
    ModeOption("Walk", Icons.AutoMirrored.Filled.DirectionsWalk),
    ModeOption("Bike", Icons.AutoMirrored.Filled.DirectionsBike),
    ModeOption("Car",   Icons.Default.DirectionsCar),
    ModeOption("Bus",   Icons.Default.DirectionsBus),
    ModeOption("Train", Icons.Default.Train),
    ModeOption("Metro", Icons.Default.Subway)
)

@Composable
private fun ModeSelector(selected: String, onSelect: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        travelModeOptions.forEach { option ->
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
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else Color.Transparent
                    )
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelect(option.label) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(option.icon, contentDescription = option.label,
                    tint     = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp))
                Spacer(Modifier.height(4.dp))
                Text(option.label, fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color      = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign  = TextAlign.Center)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PURPOSE CHIPS
// ─────────────────────────────────────────────────────────────────────────────

private val purposeOptions = listOf(
    "Work", "Home", "Shopping", "Meals",
    "Recreation", "Social", "Education", "Healthcare", "Other"
)

@Composable
private fun PurposeSelector(selected: String, onSelect: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement   = Arrangement.spacedBy(8.dp)
    ) {
        purposeOptions.forEach { purpose ->
            val isSelected = selected == purpose
            FilterChip(
                selected = isSelected,
                onClick  = { onSelect(purpose) },
                label    = {
                    Text(purpose, fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor     = MaterialTheme.colorScheme.onPrimary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled             = true,
                    selected            = isSelected,
                    borderColor         = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
                    selectedBorderColor = Color.Transparent
                )
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COMPANIONS STEPPER
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CompanionsStepper(value: Int, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    Surface(
        shape    = RoundedCornerShape(12.dp),
        color    = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(4.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = onDecrease, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Remove, "Decrease",
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            }
            AnimatedContent(
                targetState  = value,
                transitionSpec = {
                    if (targetState > initialState)
                        slideInVertically { -it } togetherWith slideOutVertically { it }
                    else
                        slideInVertically { it } togetherWith slideOutVertically { -it }
                },
                label = "companionCount"
            ) { count ->
                Text(count.toString(), fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 28.dp))
            }
            IconButton(onClick = onIncrease, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Add, "Increase",
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COST FIELD
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CostField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value         = value,
        onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) onChange(it) },
        modifier      = Modifier.fillMaxWidth(),
        placeholder   = { Text("0.00", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) },
        prefix        = { Text("₹ ", fontWeight = FontWeight.Medium) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine    = true,
        shape         = RoundedCornerShape(12.dp),
        colors        = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// SAVE BUTTON
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SaveButton(isSaving: Boolean, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue   = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label         = "btnScale"
    )

    Button(
        onClick   = { pressed = true; onClick() },
        modifier  = Modifier.fillMaxWidth().height(56.dp).scale(scale),
        enabled   = !isSaving,
        shape     = RoundedCornerShape(16.dp),
        colors    = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
    ) {
        AnimatedContent(
            targetState  = isSaving,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label        = "btnContent"
        ) { saving ->
            if (saving) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Saving…", color = MaterialTheme.colorScheme.onPrimary)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Check, null,
                        tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                    Text("Save Trip", color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PREVIEW
// ─────────────────────────────────────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PreviewTripDetailsScreen() {
    AutoTripTheme { TripDetailsScreen(rememberNavController(), tripId = "preview-001") }
}