package com.example.autotrip.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.autotrip.components.AutoTripTopBar
import com.example.autotrip.ui.theme.AutoTripTheme
import com.example.autotrip.viewmodel.AuthViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveTrackingScreen(navController: NavController, authViewModel: AuthViewModel? = null) {

    var showBottomCard by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(150); showBottomCard = true }

    Scaffold(
        topBar = {
            AutoTripTopBar(
                navController = navController,
                currentRoute = "active_tracking",
                title = "Active Tracking",
                authViewModel = authViewModel
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.55f)) {
                AnimatedPlaceholderMap()
            }
            AnimatedVisibility(
                visible = showBottomCard,
                enter = fadeIn(tween(600)) + slideInVertically { it / 2 },
                exit = fadeOut()
            ) {
                TrackingStatsSection(onEndTrip = { navController.navigate("trip_details/AT-001") })
            }
        }
    }
}

@Composable
private fun AnimatedPlaceholderMap() {
    val infinite = rememberInfiniteTransition(label = "pulse")
    val pulse by infinite.animateFloat(
        initialValue = 1f, targetValue = 1.4f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearOutSlowInEasing), RepeatMode.Reverse)
    )

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFE1E1E1))) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawLine(
                color = Color(0xFF1976D2),
                start = Offset(size.width * 0.15f, size.height * 0.52f),
                end = Offset(size.width * 0.85f, size.height * 0.52f),
                strokeWidth = 10f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(25f, 18f))
            )
        }
        Box(modifier = Modifier.offset(x = 35.dp, y = 180.dp).size(26.dp).background(Color(0xFF2E7D32), CircleShape), contentAlignment = Alignment.Center) {
            Text("S", color = Color.White, fontSize = 12.sp)
        }
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.offset(x = maxWidth * 0.78f, y = 180.dp).size((24.dp * pulse)), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.size(22.dp).background(Color(0xFF1976D2), CircleShape))
            }
        }
        Text(
            "Tracking your route...", color = Color.White,
            modifier = Modifier.align(Alignment.TopCenter).padding(12.dp).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 14.dp, vertical = 6.dp),
            fontSize = 14.sp
        )
    }
}

@Composable
private fun TrackingStatsSection(onEndTrip: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().height(300.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                TrackingStat("Duration", "00:12:45", Icons.Default.Schedule)
                TrackingStat("Distance", "3.4 km", Icons.Default.Directions)
            }
            var pressed by remember { mutableStateOf(false) }
            val scale by animateFloatAsState(targetValue = if (pressed) 0.92f else 1f, animationSpec = tween(150), label = "")
            Button(
                onClick = { pressed = true; onEndTrip() },
                modifier = Modifier.fillMaxWidth().height(55.dp).scale(scale),
                colors = ButtonDefaults.buttonColors(Color(0xFFD32F2F)),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Default.Stop, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("End Trip", color = Color.White, fontSize = 16.sp)
            }
            Text("AutoTrip is actively recording using GPS…", fontSize = 12.sp, color = Color.Gray)
        }
    }
}

@Composable
fun TrackingStat(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(6.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 12.sp, color = Color.Gray)
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PreviewActiveTrackingScreen() {
    AutoTripTheme { ActiveTrackingScreen(rememberNavController()) }
}