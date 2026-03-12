package com.example.autotrip.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTripsScreen(
    navController  : NavController,
    authViewModel  : AuthViewModel?  = null,
    tripsViewModel : TripsViewModel  = viewModel()
) {
    val allTrips by tripsViewModel.trips.collectAsState()

    var weekStart    by remember { mutableStateOf(LocalDate.now().with(DayOfWeek.MONDAY)) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }

    LaunchedEffect(weekStart) {
        if (selectedDate < weekStart || selectedDate >= weekStart.plusDays(7)) {
            selectedDate = weekStart
        }
    }

    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }

    val tripsForDay = remember(allTrips, selectedDate) {
        val key = selectedDate.format(dateFormatter)
        allTrips.filter { it.date == key }
    }

    val daysWithTrips = remember(allTrips, weekStart) {
        val weekDates = (0..6).map { weekStart.plusDays(it.toLong()) }.toSet()
        allTrips
            .mapNotNull { runCatching { LocalDate.parse(it.date, dateFormatter) }.getOrNull() }
            .filter { it in weekDates }
            .toSet()
    }

    // Delete confirmation state
    var tripToDelete by remember { mutableStateOf<Trip?>(null) }

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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            WeekStrip(
                weekStart     = weekStart,
                selectedDate  = selectedDate,
                daysWithTrips = daysWithTrips,
                onDaySelected = { selectedDate = it },
                onPrevWeek    = { weekStart = weekStart.minusWeeks(1) },
                onNextWeek    = { weekStart = weekStart.plusWeeks(1) }
            )

            val dayLabel = when (selectedDate) {
                LocalDate.now()              -> "Today"
                LocalDate.now().minusDays(1) -> "Yesterday"
                LocalDate.now().plusDays(1)  -> "Tomorrow"
                else -> selectedDate.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy"))
            }

            Row(
                modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(dayLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                AnimatedContent(
                    targetState  = tripsForDay.size,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label        = "count"
                ) { count ->
                    Text(
                        if (count == 0) "No trips" else "$count trip${if (count == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (count == 0) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.primary
                    )
                }
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
                        onTripClick  = { navController.navigate("trip_details/${it.id}") },
                        onDeleteTrip = { tripToDelete = it }
                    )
                }
            }
        }
    }

    // Delete confirmation dialog
    tripToDelete?.let { trip ->
        AlertDialog(
            onDismissRequest = { tripToDelete = null },
            icon = {
                Icon(Icons.Default.Delete, contentDescription = null,
                    tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp))
            },
            title = { Text("Delete Trip?", fontWeight = FontWeight.Bold) },
            text  = {
                Text(
                    "This will permanently remove the trip from ${trip.origin} to ${trip.destination}. This cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        tripsViewModel.deleteTrip(trip.id)
                        tripToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
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
                IconButton(onClick = onPrevWeek, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.ArrowBackIosNew, contentDescription = "Prev week",
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                }
                Text(monthLabel, style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = onNextWeek, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = "Next week",
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                (0..6).forEach { offset ->
                    val date = weekStart.plusDays(offset.toLong())
                    WeekDayCell(
                        date       = date,
                        isSelected = date == selectedDate,
                        isToday    = date == today,
                        hasTrips   = date in daysWithTrips,
                        onClick    = { onDaySelected(date) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekDayCell(
    date       : LocalDate,
    isSelected : Boolean,
    isToday    : Boolean,
    hasTrips   : Boolean,
    onClick    : () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue   = if (isSelected) 1.08f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label         = "scale"
    )

    val bgColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday    -> MaterialTheme.colorScheme.primaryContainer
        else       -> Color.Transparent
    }
    val labelColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isToday    -> MaterialTheme.colorScheme.primary
        else       -> MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = Modifier
            .width(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(
                width = if (isToday && !isSelected) 1.dp else 0.dp,
                color = if (isToday && !isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Day letter — M T W T F S S
        Text(
            date.dayOfWeek.name.take(1),
            fontSize   = 10.sp,
            fontWeight = FontWeight.Medium,
            color      = labelColor.copy(alpha = 0.65f)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            date.dayOfMonth.toString(),
            fontSize   = 15.sp,
            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
            color      = labelColor
        )
        Spacer(Modifier.height(3.dp))
        // Reserve space even when no dot, so all cells have equal height
        Box(
            modifier = Modifier
                .size(4.dp)
                .background(
                    color = if (hasTrips) {
                        if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.primary
                    } else Color.Transparent,
                    shape = CircleShape
                )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TRIP LIST
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TripTimelineList(
    trips        : List<Trip>,
    onTripClick  : (Trip) -> Unit,
    onDeleteTrip : (Trip) -> Unit
) {
    LazyColumn(
        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        itemsIndexed(trips) { index, trip ->
            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(trip.id) { delay(index * 70L); visible = true }
            AnimatedVisibility(
                visible = visible,
                enter   = fadeIn(tween(280)) + slideInVertically(tween(280)) { it / 2 }
            ) {
                TripTimelineRow(
                    trip         = trip,
                    isLast       = index == trips.lastIndex,
                    onClick      = { onTripClick(trip) },
                    onDeleteTrip = { onDeleteTrip(trip) }
                )
            }
        }
    }
}

@Composable
private fun TripTimelineRow(
    trip         : Trip,
    isLast       : Boolean,
    onClick      : () -> Unit,
    onDeleteTrip : () -> Unit
) {
    val statusColor = when (trip.status) {
        "Auto-logged" -> Color(0xFF2E7D32)
        "Needs Info"  -> Color(0xFFFF8F00)
        else          -> Color.Gray
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        // Spine
        Column(modifier = Modifier.width(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .size(11.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(60.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                )
            }
        }
        Spacer(Modifier.width(8.dp))

        Card(
            onClick   = onClick,
            modifier  = Modifier.fillMaxWidth().padding(bottom = if (isLast) 0.dp else 12.dp),
            shape     = RoundedCornerShape(14.dp),
            colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(3.dp)
        ) {
            Row(
                modifier          = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier         = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(modeIcon(trip.travelMode), contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("${trip.origin}  →  ${trip.destination}",
                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${trip.startTime} – ${trip.endTime}",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (trip.purpose.isNotBlank()) {
                            Text("·", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(trip.purpose, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(shape = RoundedCornerShape(20.dp), color = statusColor.copy(alpha = 0.12f)) {
                        Text(trip.status, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = statusColor)
                    }
                    // Delete icon
                    Icon(
                        Icons.Default.Delete, contentDescription = "Delete trip",
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.size(16.dp).clickable(onClick = onDeleteTrip)
                    )
                }
            }
        }
    }
}

private fun modeIcon(mode: String): ImageVector = when (mode.lowercase()) {
    "walk", "walking"  -> Icons.AutoMirrored.Filled.DirectionsWalk
    "bike", "bicycle"  -> Icons.AutoMirrored.Filled.DirectionsBike
    "bus"              -> Icons.Default.DirectionsBus
    "train"            -> Icons.Default.Train
    "metro"            -> Icons.Default.Subway
    else               -> Icons.Default.DirectionsCar
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