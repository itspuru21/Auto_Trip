package com.example.autotrip.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.autotrip.components.AutoTripTopBar
import com.example.autotrip.model.Trip
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailsScreen(
    navController: NavController,
    tripId: String
) {

    // Fake loading delay
    var loadedTrip by remember { mutableStateOf<Trip?>(null) }
    LaunchedEffect(Unit) {
        delay(200)
        loadedTrip = Trip(
            id = tripId,
            origin = "123 Main Street",
            destination = "City Mall",
            startTime = "09:00 AM",
            endTime = "09:30 AM",
            travelMode = "Car",
            purpose = "Shopping",
            companions = 1,
            cost = 150.0,
            status = "Needs Info"
        )
    }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(loadedTrip) { if (loadedTrip != null) visible = true }

    Scaffold(
        topBar = {
            AutoTripTopBar(
                navController = navController,
                currentRoute = "trip-details/{tripId}",
                title = "Log Your Trip"
            )
        }
    ) { padding ->

        if (loadedTrip == null) {
            // Centered loading state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val trip = loadedTrip!!

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(500)) + slideInVertically(tween(500))
            ) {
                TripDetailsForm(trip)
            }

            Spacer(Modifier.height(24.dp))

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(600)) + slideInVertically(tween(600))
            ) {
                SaveTripButton {
                    navController.navigate("home") {
                        popUpTo("active_tracking") { inclusive = true }
                    }
                }
            }

            Spacer(Modifier.height(30.dp))
        }
    }
}

/* =======================================================================================
   TRIP DETAILS FORM (OLD FORM RESTORED)
======================================================================================= */

@Composable
fun TripDetailsForm(trip: Trip) {

    var selectedMode by remember { mutableStateOf(trip.travelMode) }
    var selectedPurpose by remember { mutableStateOf(trip.purpose) }
    var companions by remember { mutableStateOf(trip.companions) }
    var cost by remember { mutableStateOf(trip.cost.toString()) }

    val travelModes = listOf("Walk", "Bike", "Car", "Bus", "Train", "Metro")
    val purposes = listOf("Work", "Home", "Shopping", "Meals", "Recreation", "Social", "Education")

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {

        /* Trip Number */
        Text("Trip Number", style = MaterialTheme.typography.titleMedium)
        Text(trip.id, color = Color.Gray)
        Spacer(Modifier.height(16.dp))

        /* Origin + Destination */
        InfoField("Origin", trip.origin)
        Spacer(Modifier.height(12.dp))
        InfoField("Destination", trip.destination)
        Spacer(Modifier.height(16.dp))

        /* Times */
        InfoField("Start Time", trip.startTime)
        Spacer(Modifier.height(12.dp))
        InfoField("End Time", trip.endTime)

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))

        /* Travel Mode */
        Text("Travel Mode", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            travelModes.forEach { mode ->
                ChoiceChip(
                    label = mode,
                    selected = selectedMode == mode,
                    onClick = { selectedMode = mode }
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        /* Trip Purpose */
        Text("Trip Purpose", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            purposes.forEach { p ->
                ChoiceChip(
                    label = p,
                    selected = selectedPurpose == p,
                    onClick = { selectedPurpose = p },
                    selectedColor = Color(0xFF43A047)
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        /* Companions */
        Text("Companions", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SmallButton("-", Color.LightGray) { if (companions > 0) companions-- }
            Text("$companions", fontSize = 22.sp)
            SmallButton("+", MaterialTheme.colorScheme.primary) { companions++ }
        }

        Spacer(Modifier.height(20.dp))

        /* Cost */
        Text("Cost (₹)", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = cost,
            onValueChange = { cost = it },
            modifier = Modifier.fillMaxWidth(),
            prefix = { Text("₹ ") }
        )
    }
}

/* =======================================================================================
   REUSABLE UI COMPONENTS
======================================================================================= */

@Composable
fun InfoField(label: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, color = Color.Gray, fontSize = 13.sp)
                Text(value, fontWeight = FontWeight.Medium)
            }
            Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun ChoiceChip(
    label: String,
    selected: Boolean,
    selectedColor: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) selectedColor else Color.Transparent
        ),
        border = if (!selected) CardDefaults.outlinedCardBorder() else null
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun SmallButton(text: String, bg: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
        colors = ButtonDefaults.buttonColors(bg),
        shape = MaterialTheme.shapes.small
    ) {
        Text(text)
    }
}

/* =======================================================================================
   SAVE BUTTON
======================================================================================= */

@Composable
fun SaveTripButton(onSave: () -> Unit) {

    var pressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = tween(150), label = ""
    )

    Button(
        onClick = {
            pressed = true
            onSave()
        },
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(55.dp)
            .scale(scale),
        colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primary)
    ) {
        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
        Spacer(Modifier.width(8.dp))
        Text("Save Trip", color = Color.White)
    }
}
