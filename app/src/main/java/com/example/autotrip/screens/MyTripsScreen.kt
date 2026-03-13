package com.example.autotrip.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.autotrip.components.AutoTripTopBar
import com.example.autotrip.components.BottomNavigationBar
import com.example.autotrip.model.Trip
import com.example.autotrip.ui.theme.AutoTripTheme
import com.example.autotrip.viewmodel.AuthViewModel
import com.example.autotrip.viewmodel.TripsViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun MyTripsScreen(
    navController  : NavController,
    authViewModel  : AuthViewModel? = null,
    tripsViewModel : TripsViewModel = viewModel()
) {
    val allTrips by tripsViewModel.trips.collectAsState()
    var tripToDelete by remember { mutableStateOf<Trip?>(null) }

    // Week navigation
    var weekStart by remember { mutableStateOf(LocalDate.now().minusDays(LocalDate.now().dayOfWeek.value.toLong() - 1)) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }

    val daysWithTrips = remember(allTrips) {
        allTrips.mapNotNull { trip ->
            runCatching { LocalDate.parse(trip.date, DateTimeFormatter.ofPattern("yyyy-MM-dd")) }.getOrNull()
        }.toSet()
    }

    val tripsForDay = remember(allTrips, selectedDate) {
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        allTrips.filter { it.date == selectedDate.format(fmt) }
            .sortedBy { it.startTime }
    }

    Scaffold(
        topBar = {
            AutoTripTopBar(
                navController = navController,
                currentRoute  = "my_trips",
                title         = "My Trips",
                authViewModel = authViewModel
            )
        },
        bottomBar = {
            BottomNavigationBar(navController = navController, currentRoute = "my_trips")
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            WeekStrip(
                weekStart     = weekStart,
                selectedDate  = selectedDate,
                daysWithTrips = daysWithTrips,
                onDaySelected = { selectedDate = it },
                onPrevWeek    = { weekStart = weekStart.minusDays(7) },
                onNextWeek    = {
                    val candidate = weekStart.plusDays(7)
                    if (!candidate.isAfter(LocalDate.now())) weekStart = candidate
                }
            )

            val count = tripsForDay.size
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(selectedDate.toString(), style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
                Text(
                    if (count == 0) "No trips" else "$count trip${if (count == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (count == 0) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary
                )
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            AnimatedContent(
                targetState  = tripsForDay,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                label        = "tripList"
            ) { trips ->
                if (trips.isEmpty()) {
                    EmptyDayState()
                } else {
                    TripTimelineList(
                        trips        = trips,
                        onDeleteTrip = { tripToDelete = it }
                        // NOTE: no onTripClick navigation — editing disabled from My Trips
                    )
                }
            }
        }
    }

    tripToDelete?.let { trip ->
        AlertDialog(
            onDismissRequest = { tripToDelete = null },
            icon = {
                Icon(Icons.Default.Delete, contentDescription = null,
                    tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp))
            },
            title = { Text("Delete Trip?", fontWeight = FontWeight.Bold) },
            text  = {
                Text("This will permanently remove the trip from ${trip.origin} to ${trip.destination}. This cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium)
            },
            confirmButton = {
                Button(
                    onClick = { tripsViewModel.deleteTrip(trip.id); tripToDelete = null },
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { tripToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// WEEK STRIP
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WeekStrip(
    weekStart     : LocalDate,
    selectedDate  : LocalDate,
    daysWithTrips : Set<LocalDate>,
    onDaySelected : (LocalDate) -> Unit,
    onPrevWeek    : () -> Unit,
    onNextWeek    : () -> Unit
) {
    val today   = LocalDate.now()
    val weekEnd = weekStart.plusDays(6)

    val monthLabel = buildString {
        val sm = weekStart.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
        val em = weekEnd.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
        append(if (weekStart.month == weekEnd.month) "$sm ${weekStart.year}" else "$sm – $em ${weekEnd.year}")
    }

    Surface(
        modifier        = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        shape           = RoundedCornerShape(16.dp),
        color           = MaterialTheme.colorScheme.surface,
        tonalElevation  = 2.dp,
        shadowElevation = 3.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onPrevWeek, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ChevronLeft, null, modifier = Modifier.size(20.dp))
                }
                Text(monthLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                IconButton(
                    onClick  = onNextWeek,
                    enabled  = weekStart.plusDays(7).isBefore(today) || weekStart.plusDays(7) == today,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(4.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                (0..6).forEach { offset ->
                    val day      = weekStart.plusDays(offset.toLong())
                    val isSel    = day == selectedDate
                    val isToday  = day == today
                    val hasTr    = day in daysWithTrips
                    val isFuture = day.isAfter(today)

                    Column(
                        modifier            = Modifier
                            .width(36.dp)
                            .clickable(enabled = !isFuture) { onDaySelected(day) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(day.dayOfWeek.name.take(1),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isFuture) MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f)
                            else MaterialTheme.colorScheme.onSurfaceVariant)

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = when {
                                isSel   -> MaterialTheme.colorScheme.primary
                                isToday -> MaterialTheme.colorScheme.primaryContainer
                                else    -> Color.Transparent
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    day.dayOfMonth.toString(),
                                    style     = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isSel || isToday) FontWeight.Bold else FontWeight.Normal,
                                    color     = when {
                                        isSel    -> MaterialTheme.colorScheme.onPrimary
                                        isFuture -> MaterialTheme.colorScheme.onSurface.copy(0.28f)
                                        else     -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }

                        // Dot when trips exist on that day
                        if (hasTr) {
                            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(4.dp)) {}
                        } else {
                            Spacer(Modifier.size(4.dp))
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TRIP TIMELINE LIST — cards expand on tap to show full details (no editing)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TripTimelineList(
    trips        : List<Trip>,
    onDeleteTrip : (Trip) -> Unit
) {
    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding      = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
    ) {
        items(trips) { trip ->
            ExpandableTripCard(trip = trip, onDeleteTrip = { onDeleteTrip(trip) })
        }
    }
}

@Composable
private fun ExpandableTripCard(
    trip         : Trip,
    onDeleteTrip : () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(3.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(12.dp)
        ) {
            // ── Header row (always visible) ───────────────────────
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(modeIcon(trip.travelMode), contentDescription = null,
                    tint     = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp))

                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text("${trip.origin} → ${trip.destination}",
                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
                    Text("${trip.startTime} – ${trip.endTime}",
                        fontSize = 12.sp, color = Color.Gray)
                }

                Spacer(Modifier.width(8.dp))

                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                    modifier = Modifier.size(20.dp)
                )

                Spacer(Modifier.width(4.dp))

                Icon(Icons.Default.Delete, contentDescription = "Delete trip",
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.45f),
                    modifier = Modifier.size(18.dp).clickable(onClick = onDeleteTrip))
            }

            // ── Expandable details ────────────────────────────────
            AnimatedVisibility(
                visible = expanded,
                enter   = expandVertically(tween(250)) + fadeIn(tween(200)),
                exit    = shrinkVertically(tween(200)) + fadeOut(tween(150))
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.15f))
                    Spacer(Modifier.height(10.dp))

                    DetailRow(Icons.Default.DirectionsCar,     "Mode",       trip.travelMode.ifBlank { "—" })
                    DetailRow(Icons.Default.Flag,               "Purpose",    trip.purpose.ifBlank { "—" })
                    DetailRow(Icons.Default.Route,             "Distance",
                        if (trip.distanceKm > 0) "%.2f km".format(trip.distanceKm) else "—")
                    if (trip.durationSecs > 0) {
                        val h = trip.durationSecs / 3600; val m = (trip.durationSecs % 3600) / 60
                        DetailRow(Icons.Default.Timer, "Duration", if (h > 0) "${h}h ${m}m" else "${m}m")
                    }
                    if (trip.companions > 0)
                        DetailRow(Icons.Default.People, "Companions", "${trip.companions}")
                    if (trip.cost > 0)
                        DetailRow(Icons.Default.CurrencyRupee, "Cost", "₹%.2f".format(trip.cost))
                    DetailRow(Icons.Default.CalendarToday, "Date", trip.date)

                    Spacer(Modifier.height(4.dp))
                    // Status chip
                    val statusColor = if (trip.status == "Auto-logged") Color(0xFF2E7D32) else Color(0xFFFF8F00)
                    Surface(shape = RoundedCornerShape(20.dp), color = statusColor.copy(0.12f)) {
                        Text(trip.status,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            color    = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier         = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text("$label: ", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

private fun modeIcon(mode: String): ImageVector = when (mode.lowercase()) {
    "walk", "walking"                     -> Icons.AutoMirrored.Filled.DirectionsWalk
    "bike", "bicycle"                     -> Icons.AutoMirrored.Filled.DirectionsBike
    "bus"                                 -> Icons.Default.DirectionsBus
    "train"                               -> Icons.Default.Train
    "auto-rickshaw", "auto", "rickshaw"   -> Icons.Default.ElectricRickshaw
    else                                  -> Icons.Default.DirectionsCar
}

// ─────────────────────────────────────────────────────────────────────────────
// EMPTY STATE
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyDayState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier.padding(32.dp)
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = null,
                tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f),
                modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(12.dp))
            Text("No trips recorded", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text("Tap + on the home screen to log a trip",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f), textAlign = TextAlign.Center)
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PreviewMyTripsScreen() {
    AutoTripTheme { MyTripsScreen(rememberNavController()) }
}