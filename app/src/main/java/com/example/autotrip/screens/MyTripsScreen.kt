package com.example.autotrip.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.autotrip.components.AutoTripTopBar
import com.example.autotrip.components.BottomNavigationBar
import com.example.autotrip.model.Trip
import com.example.autotrip.ui.theme.AutoTripTheme
import kotlinx.coroutines.delay
import java.time.LocalDate // ⚠️ Requires minSdk 26+ or core library desugaring
import java.time.format.DateTimeFormatter


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTripsScreen(navController: NavController) {

    var selectedDate by remember { mutableStateOf(LocalDate.now()) }

    // Temporary display trips (same every month for now)
    val trips = listOf(
        Trip("1", "Home", "Work", "08:30 AM", "09:15 AM", "Car", "Work", 0, 0.0, "Auto-logged"),
        Trip("2", "Work", "Lunch Spot", "12:00 PM", "12:30 PM", "Walk", "Meals", 2, 0.0, "Auto-logged"),
        Trip("3", "Lunch Spot", "Gym", "01:00 PM", "01:15 PM", "Walk", "Recreation", 0, 0.0, "Needs Info"),
        Trip("4", "Gym", "Home", "05:00 PM", "05:45 PM", "Car", "Home", 1, 0.0, "Auto-logged")
    )

    // enter animation for whole screen
    var screenVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { screenVisible = true }

    Scaffold(
        topBar = {
            AutoTripTopBar(
                navController = navController,
                currentRoute = "my_trips",
                title = "My Trips"
            )
        },
        bottomBar = {
            BottomNavigationBar(
                navController = navController,
                currentRoute = "my_trips"
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
        {

            // --------- Animated Month Header ---------
            AnimatedVisibility(
                visible = screenVisible,
                enter = fadeIn(tween(500)) + slideInVertically(tween(500)),
                exit = fadeOut()
            ) {
                MonthSwitcher(
                    selectedDate = selectedDate,
                    onChange = { selectedDate = it }
                )
            }

            Spacer(Modifier.height(20.dp))

            // --------- Animated Trip Timeline ---------
            AnimatedTripTimeline(trips)
        }
    }
}


@Composable
private fun MonthSwitcher(
    selectedDate: LocalDate,
    onChange: (LocalDate) -> Unit
) {

    val formatter = DateTimeFormatter.ofPattern("MMMM yyyy")

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {

        IconButton(onClick = {
            onChange(selectedDate.minusMonths(1))
        }) {
            Icon(
                Icons.Default.ArrowBackIosNew,
                contentDescription = "Previous Month",
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Text(
            selectedDate.format(formatter),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        IconButton(onClick = {
            onChange(selectedDate.plusMonths(1))
        }) {
            Icon(
                Icons.Default.ArrowForwardIos,
                contentDescription = "Next Month",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}


@Composable
private fun AnimatedTripTimeline(trips: List<Trip>) {

    // fade-in for whole list
    var listVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { listVisible = true }

    val listAlpha by animateFloatAsState(
        targetValue = if (listVisible) 1f else 0f,
        animationSpec = tween(600),
        label = ""
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .alpha(listAlpha),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        itemsIndexed(trips) { index, trip ->

            // stagger animation for each item
            var itemVisible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(index * 120L)
                itemVisible = true
            }

            val offsetY by animateDpAsState(
                targetValue = if (itemVisible) 0.dp else 20.dp,
                animationSpec = tween(450),
                label = ""
            )
            val alpha by animateFloatAsState(
                targetValue = if (itemVisible) 1f else 0f,
                animationSpec = tween(450),
                label = ""
            )

            Box(modifier = Modifier.offset(y = offsetY).alpha(alpha)) {
                TripTimelineItem(trip)
            }
        }
    }
}


@Composable
private fun TripTimelineItem(trip: Trip) {

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {

        Row(modifier = Modifier.padding(16.dp)) {

            // --- Timeline Dot + Line ---
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )

                Spacer(
                    modifier = Modifier
                        .height(40.dp)
                        .width(2.dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }

            Spacer(Modifier.width(16.dp))

            // --- Trip Data ---
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${trip.origin} → ${trip.destination}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    "${trip.startTime} - ${trip.endTime}",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
                Text(
                    trip.purpose,
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PreviewMyTripsScreen() {
    AutoTripTheme {
        MyTripsScreen(rememberNavController())
    }
}
