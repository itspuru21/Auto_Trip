package com.example.autotrip.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
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
import java.text.SimpleDateFormat
import java.util.*

// ─────────────────────────────────────────────────────────────────────────────
// "Today" = from 12:00 AM to 11:59:59 PM of the current calendar day
// ─────────────────────────────────────────────────────────────────────────────

private fun isTodayTrip(trip: Trip): Boolean {
    val todayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val today    = todayFmt.format(Date())
    return trip.date == today
}

@Composable
fun HomeScreen(
    navController : NavController,
    authViewModel : AuthViewModel? = null
) {
    val tripsVm: TripsViewModel = viewModel()
    val allTrips by tripsVm.trips.collectAsState()

    // Today 12AM–12AM filter
    val todayTrips = remember(allTrips) { allTrips.filter { isTodayTrip(it) } }

    Scaffold(
        topBar = {
            AutoTripTopBar(
                navController = navController,
                currentRoute  = "home",
                title         = "Home",
                authViewModel = authViewModel
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick        = { navController.navigate("active_tracking") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor   = MaterialTheme.colorScheme.onPrimary,
                icon           = { Icon(Icons.Default.Add, contentDescription = "New Trip") },
                text           = { Text("New Trip", fontWeight = FontWeight.SemiBold) }
            )
        },
        floatingActionButtonPosition = FabPosition.End,
        bottomBar = {
            BottomNavigationBar(navController = navController, currentRoute = "home")
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            SummaryCard(trips = todayTrips)

            Spacer(Modifier.height(24.dp))

            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Today's Trips",
                    style = MaterialTheme.typography.titleLarge, fontSize = 20.sp,
                    fontWeight = FontWeight.Bold)
                Text("Tap to edit",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }

            Spacer(Modifier.height(8.dp))

            Text("Trips are editable today, confirmed at midnight.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))

            Spacer(Modifier.height(12.dp))

            if (todayTrips.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.DirectionsCar, contentDescription = null,
                            modifier = Modifier.size(52.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                        Text("No trips today yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        Text("Tap + New Trip to start tracking",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                    }
                }
            } else {
                // Proper scrollable list with weight so FAB doesn't overlap
                AnimatedTripsList(
                    trips       = todayTrips,
                    onTripClick = { trip -> navController.navigate("trip_details/${trip.id}") },
                    modifier    = Modifier.weight(1f)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SUMMARY CARD — Trips | Distance | Time  (no "Pending")
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SummaryCard(trips: List<Trip> = emptyList()) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val cardAlpha by animateFloatAsState(targetValue = if (visible) 1f else 0f,
        animationSpec = tween(650), label = "cardAlpha")
    val offsetY by animateDpAsState(targetValue = if (visible) 0.dp else 16.dp,
        animationSpec = tween(650), label = "cardOffset")

    val tripCount        = trips.size
    val totalDistanceKm  = trips.sumOf { it.distanceKm }
    val totalDurationSec = trips.sumOf { it.durationSecs }

    val distanceLabel = when {
        tripCount == 0      -> "—"
        totalDistanceKm <= 0 -> "—"
        else                 -> "%.1f km".format(totalDistanceKm)
    }

    val timeLabel = when {
        tripCount == 0        -> "—"
        totalDurationSec <= 0 -> "—"
        else -> {
            val h = totalDurationSec / 3600
            val m = (totalDurationSec % 3600) / 60
            if (h > 0) "${h}h ${m}m" else "${m}m"
        }
    }

    Card(
        modifier  = Modifier.fillMaxWidth().offset(y = offsetY).alpha(cardAlpha),
        elevation = CardDefaults.cardElevation(6.dp),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Today's Summary", style = MaterialTheme.typography.titleLarge, fontSize = 20.sp)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                SummaryItem(Icons.Default.Route,         "Trips",    tripCount.toString())
                SummaryItem(Icons.Default.DirectionsCar, "Distance", distanceLabel)
                SummaryItem(Icons.Default.Timer,         "Time",     timeLabel)
            }
        }
    }
}

@Composable
fun SummaryItem(icon: ImageVector, label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(6.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontSize = 18.sp,
            fontWeight = FontWeight.Bold)
        Text(label, fontSize = 12.sp, color = Color.Gray)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TRIPS LIST — animated, properly scrollable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AnimatedTripsList(
    trips       : List<Trip>,
    onTripClick : (Trip) -> Unit,
    modifier    : Modifier = Modifier
) {
    var listVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { listVisible = true }
    val alpha by animateFloatAsState(targetValue = if (listVisible) 1f else 0f,
        animationSpec = tween(500), label = "listAlpha")

    LazyColumn(
        modifier            = modifier.alpha(alpha),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding      = PaddingValues(bottom = 88.dp) // space for FAB
    ) {
        itemsIndexed(trips) { index, trip ->
            var itemVisible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { delay(index * 120L); itemVisible = true }
            AnimatedVisibility(visible = itemVisible,
                enter = fadeIn(tween(450)) + slideInVertically(tween(450))) {
                TripItemCard(trip = trip, onClick = { onTripClick(trip) })
            }
        }
    }
}

@Composable
fun TripItemCard(trip: Trip, onClick: () -> Unit) {
    val statusColor = when (trip.status) {
        "Auto-logged" -> Color(0xFF2E7D32)
        "Needs Info"  -> Color(0xFFFF8F00)
        else          -> Color.Gray
    }
    Card(
        onClick   = onClick,
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${trip.origin} → ${trip.destination}",
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text("${trip.startTime} – ${trip.endTime}", fontSize = 13.sp, color = Color.Gray)
                if (trip.distanceKm > 0) {
                    Spacer(Modifier.height(2.dp))
                    Text("%.2f km".format(trip.distanceKm),
                        fontSize = 12.sp,
                        color    = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                        fontWeight = FontWeight.Medium)
                }
            }
            Surface(shape = RoundedCornerShape(20.dp), color = statusColor.copy(alpha = 0.12f)) {
                Text(trip.status,
                    modifier   = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    color      = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PREVIEW
// ─────────────────────────────────────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PreviewHomeScreen() {
    AutoTripTheme { HomeScreen(rememberNavController()) }
}