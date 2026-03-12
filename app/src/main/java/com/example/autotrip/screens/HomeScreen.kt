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
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.autotrip.components.AutoTripTopBar
import com.example.autotrip.components.BottomNavigationBar
import com.example.autotrip.model.Trip
import com.example.autotrip.ui.theme.AutoTripTheme
import com.example.autotrip.viewmodel.AuthViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController, authViewModel: AuthViewModel? = null) {

    val trips = remember {
        listOf(
            Trip("1", "Home", "Work", "8:30 AM", "9:15 AM", "Car", "Work", 0, 0.0, "Auto-logged"),
            Trip("2", "Work", "Coffee Shop", "12:00 PM", "12:15 PM", "Walk", "Social", 2, 0.0, "Needs Info"),
            Trip("3", "Coffee Shop", "Park", "1:30 PM", "2:00 PM", "Walk", "Recreation", 1, 0.0, "Auto-logged")
        )
    }

    Scaffold(
        topBar = {
            AutoTripTopBar(
                navController = navController,
                currentRoute = "home",
                title = "Home",
                authViewModel = authViewModel
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("active_tracking") },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Start Tracking", tint = Color.White)
            }
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
            SummaryCard()
            Spacer(Modifier.height(24.dp))
            Text("Recent Trips", style = MaterialTheme.typography.titleLarge, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(12.dp))
            AnimatedTripsList(trips = trips, onTripClick = { trip -> navController.navigate("trip_details/${trip.id}") })
        }
    }
}

@Composable
fun SummaryCard() {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val cardAlpha by animateFloatAsState(targetValue = if (visible) 1f else 0f, animationSpec = tween(650), label = "")
    val offsetY by animateDpAsState(targetValue = if (visible) 0.dp else 16.dp, animationSpec = tween(650), label = "")

    Card(
        modifier = Modifier.fillMaxWidth().offset(y = offsetY).alpha(cardAlpha),
        elevation = CardDefaults.cardElevation(6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Today's Summary", style = MaterialTheme.typography.titleLarge, fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                SummaryItem(Icons.Default.Route, "Trips", "2")
                SummaryItem(Icons.Default.DirectionsCar, "Distance", "15.3 km")
                SummaryItem(Icons.Default.Timer, "Time", "48 min")
            }
        }
    }
}

@Composable
fun SummaryItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(6.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Text(label, fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
fun AnimatedTripsList(trips: List<Trip>, onTripClick: (Trip) -> Unit) {
    var listVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { listVisible = true }

    val alpha by animateFloatAsState(targetValue = if (listVisible) 1f else 0f, animationSpec = tween(500), label = "")

    LazyColumn(modifier = Modifier.fillMaxSize().alpha(alpha), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        itemsIndexed(trips) { index, trip ->
            var itemVisible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { delay(index * 120L); itemVisible = true }
            AnimatedVisibility(visible = itemVisible, enter = fadeIn(tween(450)) + slideInVertically(tween(450))) {
                TripItemCard(trip = trip, onClick = { onTripClick(trip) })
            }
        }
    }
}

@Composable
fun TripItemCard(trip: Trip, onClick: () -> Unit) {
    val statusColor = when (trip.status) {
        "Auto-logged" -> Color(0xFF2E7D32)
        "Needs Info" -> Color(0xFFFF8F00)
        else -> Color.Gray
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${trip.origin} → ${trip.destination}", fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text("${trip.startTime} - ${trip.endTime}", fontSize = 13.sp, color = Color.Gray)
            }
            Text(trip.status, color = statusColor, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PreviewHomeScreen() {
    AutoTripTheme { HomeScreen(rememberNavController()) }
}