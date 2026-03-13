package com.example.autotrip.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.autotrip.components.AutoTripTopBar
import com.example.autotrip.simulation.SimMode
import com.example.autotrip.simulation.SimPreset
import com.example.autotrip.simulation.SimPresets
import com.example.autotrip.ui.theme.AutoTripTheme
import com.example.autotrip.viewmodel.AuthViewModel
import java.net.URLEncoder

/**
 * Dev Tools — GPS Trip Simulator configuration screen.
 *
 * IMPORTANT: This screen does NOT touch ActiveTrackingViewModel at all.
 * It only builds the route parameters and navigates to ActiveTrackingSimScreen,
 * which owns its own ViewModel instance and starts the simulation itself.
 *
 * Previous bug: DevToolsScreen called viewModel() + startSimulation(), but
 * ActiveTrackingSimScreen called viewModel() again → different instance →
 * simulation ran in a ViewModel nobody was observing. Everything was blank.
 */
@Composable
fun DevToolsScreen(
    navController : NavController,
    authViewModel : AuthViewModel? = null
) {
    var selectedMode by remember { mutableStateOf(SimMode.CAR) }

    var originPreset by remember { mutableStateOf(SimPresets.DEFAULT_ORIGIN) }
    var destPreset   by remember { mutableStateOf(SimPresets.DEFAULT_DESTINATION) }

    var originCustomLat by remember { mutableStateOf("") }
    var originCustomLng by remember { mutableStateOf("") }
    var destCustomLat   by remember { mutableStateOf("") }
    var destCustomLng   by remember { mutableStateOf("") }

    var showOriginPicker by remember { mutableStateOf(false) }
    var showDestPicker   by remember { mutableStateOf(false) }

    val isCustomOrigin = originPreset.name == "Custom"
    val isCustomDest   = destPreset.name   == "Custom"

    val resolvedOrigin: SimPreset? = if (isCustomOrigin) {
        val lat = originCustomLat.toDoubleOrNull()
        val lng = originCustomLng.toDoubleOrNull()
        if (lat != null && lng != null) SimPreset("Custom Origin", lat, lng) else null
    } else originPreset

    val resolvedDest: SimPreset? = if (isCustomDest) {
        val lat = destCustomLat.toDoubleOrNull()
        val lng = destCustomLng.toDoubleOrNull()
        if (lat != null && lng != null) SimPreset("Custom Dest", lat, lng) else null
    } else destPreset

    val canLaunch = resolvedOrigin != null && resolvedDest != null &&
            !(resolvedOrigin.lat == resolvedDest.lat && resolvedOrigin.lng == resolvedDest.lng)

    Scaffold(
        topBar = {
            AutoTripTopBar(
                navController = navController,
                currentRoute  = "dev_tools",
                title         = "🛠 Dev Tools — GPS Simulator",
                authViewModel = authViewModel
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Debug banner ─────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFFF6F00).copy(alpha = 0.12f)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.BugReport, null,
                        tint = Color(0xFFFF6F00), modifier = Modifier.size(20.dp))
                    Text(
                        "Debug only — not visible in release builds.\n" +
                                "Simulation fetches a real road route, then plays it at 10× speed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFF6F00)
                    )
                }
            }

            // ── Transport mode ────────────────────────────────────
            DevSection("Transport Mode") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SimMode.entries.forEach { mode ->
                        val selected = mode == selectedMode
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { selectedMode = mode }
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) Color(0xFFFF6F00)
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(10.dp)
                                ),
                            color = if (selected) Color(0xFFFF6F00).copy(alpha = 0.10f)
                            else MaterialTheme.colorScheme.surface
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(mode.emoji, fontSize = 18.sp)
                                Text(
                                    mode.label.split("-").first().split(" ").first(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Simulated at 10× · Effective: ${(selectedMode.avgSpeedKmh * 10).toInt()} km/h",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            // ── Origin ────────────────────────────────────────────
            DevSection("Origin") {
                CoordRow(
                    preset       = originPreset,
                    customLat    = originCustomLat,
                    customLng    = originCustomLng,
                    onPickClick  = { showOriginPicker = true },
                    onLatChange  = { originCustomLat = it },
                    onLngChange  = { originCustomLng = it }
                )
            }

            // ── Destination ───────────────────────────────────────
            DevSection("Destination") {
                CoordRow(
                    preset       = destPreset,
                    customLat    = destCustomLat,
                    customLng    = destCustomLng,
                    onPickClick  = { showDestPicker = true },
                    onLatChange  = { destCustomLat = it },
                    onLngChange  = { destCustomLng = it }
                )
            }

            // ── Route info ────────────────────────────────────────
            if (resolvedOrigin != null && resolvedDest != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Text(
                            "Route will follow real roads via OSRM. " +
                                    "Falls back to straight-line if offline.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // ── Launch button ─────────────────────────────────────
            Button(
                onClick = {
                    val orig = resolvedOrigin ?: return@Button
                    val dest = resolvedDest   ?: return@Button

                    // Pass everything via nav args — ActiveTrackingSimScreen
                    // creates its OWN ViewModel and starts the simulation.
                    // This is the correct pattern: one screen = one ViewModel.
                    navController.navigate(
                        "active_tracking_sim" +
                                "/${URLEncoder.encode(orig.name, "UTF-8")}" +
                                "/${orig.lat}/${orig.lng}" +
                                "/${URLEncoder.encode(dest.name, "UTF-8")}" +
                                "/${dest.lat}/${dest.lng}" +
                                "/${selectedMode.name}"
                    )
                },
                enabled  = canLaunch,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape    = RoundedCornerShape(16.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))
            ) {
                Icon(Icons.Default.PlayArrow, null,
                    tint = Color.White, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Launch Simulation", color = Color.White,
                    fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showOriginPicker) {
        PresetPickerDialog(
            title     = "Select Origin",
            onSelect  = { originPreset = it; showOriginPicker = false },
            onDismiss = { showOriginPicker = false }
        )
    }
    if (showDestPicker) {
        PresetPickerDialog(
            title     = "Select Destination",
            onSelect  = { destPreset = it; showDestPicker = false },
            onDismiss = { showDestPicker = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SUB-COMPOSABLES
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DevSection(
    title   : String,
    content : @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun CoordRow(
    preset      : SimPreset,
    customLat   : String,
    customLng   : String,
    onPickClick : () -> Unit,
    onLatChange : (String) -> Unit,
    onLngChange : (String) -> Unit
) {
    val isCustom = preset.name == "Custom"
    OutlinedButton(
        onClick  = onPickClick,
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(10.dp)
    ) {
        Icon(Icons.Default.Place, null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (isCustom) "Custom coordinates" else preset.name,
            modifier = Modifier.weight(1f))
        Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(18.dp))
    }
    AnimatedVisibility(visible = isCustom) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value         = customLat,
                onValueChange = onLatChange,
                label         = { Text("Latitude") },
                modifier      = Modifier.weight(1f),
                singleLine    = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            OutlinedTextField(
                value         = customLng,
                onValueChange = onLngChange,
                label         = { Text("Longitude") },
                modifier      = Modifier.weight(1f),
                singleLine    = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        }
    }
}

@Composable
private fun PresetPickerDialog(
    title     : String,
    onSelect  : (SimPreset) -> Unit,
    onDismiss : () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text(title) },
        text    = {
            Column {
                SimPresets.ALL.forEach { preset ->
                    TextButton(
                        onClick  = { onSelect(preset) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(preset.name, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewDevToolsScreen() {
    AutoTripTheme { DevToolsScreen(rememberNavController()) }
}