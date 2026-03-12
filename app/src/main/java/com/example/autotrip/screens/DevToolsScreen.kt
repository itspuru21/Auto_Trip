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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.autotrip.components.AutoTripTopBar
import com.example.autotrip.location.SimulatedLocationProvider
import com.example.autotrip.simulation.SimMode
import com.example.autotrip.simulation.SimPreset
import com.example.autotrip.simulation.SimPresets
import com.example.autotrip.ui.theme.AutoTripTheme
import com.example.autotrip.viewmodel.ActiveTrackingViewModel
import com.example.autotrip.viewmodel.AuthViewModel

/**
 * Dev Tools — GPS Trip Simulator
 *
 * Visible only in DEBUG builds. Accessible from the Profile screen.
 * Configures and launches a [SimulatedLocationProvider] into
 * [ActiveTrackingViewModel] so the tracking UI behaves as if the
 * user is actually on a trip — no need to go outside.
 *
 * Features:
 *  - Transport mode selector (Walk / Bike / Auto / Bus / Car / Metro)
 *  - Origin & Destination via built-in presets OR custom lat/lng
 *  - Speed info label showing effective simulated speed (10x)
 *  - "Launch Simulation" → navigates to ActiveTrackingScreen with sim running
 */
@Composable
fun DevToolsScreen(
    navController : NavController,
    authViewModel : AuthViewModel? = null
) {
    // Shared ViewModel — same instance ActiveTrackingScreen will receive
    val trackingVm: ActiveTrackingViewModel = viewModel()

    // ── State ────────────────────────────────────────────────────
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

    // Resolve final coords
    val resolvedOrigin = if (isCustomOrigin) {
        val lat = originCustomLat.toDoubleOrNull()
        val lng = originCustomLng.toDoubleOrNull()
        if (lat != null && lng != null) SimPreset("Custom Origin", lat, lng) else null
    } else originPreset

    val resolvedDest = if (isCustomDest) {
        val lat = destCustomLat.toDoubleOrNull()
        val lng = destCustomLng.toDoubleOrNull()
        if (lat != null && lng != null) SimPreset("Custom Dest", lat, lng) else null
    } else destPreset

    val canLaunch = resolvedOrigin != null && resolvedDest != null &&
            resolvedOrigin.lat != resolvedDest.lat

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
                    modifier          = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.BugReport, contentDescription = null,
                        tint     = Color(0xFFFF6F00),
                        modifier = Modifier.size(20.dp))
                    Text(
                        "Debug only — not visible in release builds.\n" +
                        "Simulation runs at 10× speed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFF6F00)
                    )
                }
            }

            // ── Mode selector ────────────────────────────────────
            DevSection(title = "Transport Mode") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Row 1: Walk, Bicycle, Auto
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()) {
                        SimMode.entries.take(3).forEach { mode ->
                            ModeChip(
                                mode       = mode,
                                isSelected = selectedMode == mode,
                                onClick    = { selectedMode = mode },
                                modifier   = Modifier.weight(1f)
                            )
                        }
                    }
                    // Row 2: Bus, Car, Metro
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()) {
                        SimMode.entries.drop(3).forEach { mode ->
                            ModeChip(
                                mode       = mode,
                                isSelected = selectedMode == mode,
                                onClick    = { selectedMode = mode },
                                modifier   = Modifier.weight(1f)
                            )
                        }
                    }
                    // Speed info
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Avg speed", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${selectedMode.avgSpeedKmh} km/h  →  sim @ " +
                                    "${selectedMode.avgSpeedKmh * 10} km/h (10×)",
                                style      = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color      = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // ── Origin ───────────────────────────────────────────
            DevSection(title = "Origin") {
                PresetDropdownButton(
                    selected  = originPreset,
                    onClick   = { showOriginPicker = true }
                )
                AnimatedVisibility(isCustomOrigin) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp)) {
                        LatLngField("Latitude",  originCustomLat)  { originCustomLat = it }
                        LatLngField("Longitude", originCustomLng)  { originCustomLng = it }
                    }
                }
            }

            // ── Destination ──────────────────────────────────────
            DevSection(title = "Destination") {
                PresetDropdownButton(
                    selected = destPreset,
                    onClick  = { showDestPicker = true }
                )
                AnimatedVisibility(isCustomDest) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp)) {
                        LatLngField("Latitude",  destCustomLat)  { destCustomLat = it }
                        LatLngField("Longitude", destCustomLng)  { destCustomLng = it }
                    }
                }
            }

            // ── Summary ──────────────────────────────────────────
            if (resolvedOrigin != null && resolvedDest != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SimSummaryRow("From", resolvedOrigin.name)
                        SimSummaryRow("To",   resolvedDest.name)
                        SimSummaryRow("Mode", "${selectedMode.emoji} ${selectedMode.label}")
                        SimSummaryRow("Speed", "${selectedMode.avgSpeedKmh} km/h (10× sim)")
                    }
                }
            }

            // ── Launch button ────────────────────────────────────
            Button(
                onClick = {
                    val orig = resolvedOrigin ?: return@Button
                    val dest = resolvedDest   ?: return@Button
                    val simProvider = SimulatedLocationProvider(
                        origin      = orig,
                        destination = dest,
                        mode        = selectedMode
                    )
                    trackingVm.startSimulation(simProvider)
                    // Navigate to tracking screen — it will detect isSimulating = true
                    navController.navigate(
                        "active_tracking_sim/${orig.name}/${dest.name}"
                    )
                },
                enabled  = canLaunch,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape    = RoundedCornerShape(16.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF6F00)
                )
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null,
                    tint     = Color.White,
                    modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Launch Simulation", color = Color.White,
                    fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // ── Preset pickers ───────────────────────────────────────────
    if (showOriginPicker) {
        PresetPickerDialog(
            title    = "Select Origin",
            onSelect = { originPreset = it; showOriginPicker = false },
            onDismiss = { showOriginPicker = false }
        )
    }
    if (showDestPicker) {
        PresetPickerDialog(
            title    = "Select Destination",
            onSelect = { destPreset = it; showDestPicker = false },
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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun ModeChip(
    mode       : SimMode,
    isSelected : Boolean,
    onClick    : () -> Unit,
    modifier   : Modifier = Modifier
) {
    val bg     = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val fg     = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val border = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(mode.emoji, fontSize = 20.sp)
        Text(
            mode.label.split("-").first(),   // "Auto" from "Auto-Rickshaw"
            style      = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color      = fg
        )
    }
}

@Composable
private fun PresetDropdownButton(selected: SimPreset, onClick: () -> Unit) {
    OutlinedButton(
        onClick  = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp)
    ) {
        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(selected.name, modifier = Modifier.weight(1f))
        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
    }
}

@Composable
private fun LatLngField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value         = value,
        onValueChange = onValueChange,
        label         = { Text(label) },
        modifier      = Modifier.fillMaxWidth(),
        singleLine    = true,
        shape         = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        placeholder   = { Text(if (label == "Latitude") "e.g. 19.8762" else "e.g. 75.3433",
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) }
    )
}

@Composable
private fun SimSummaryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetPickerDialog(
    title     : String,
    onSelect  : (SimPreset) -> Unit,
    onDismiss : () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SimPresets.ALL.forEach { preset ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelect(preset) }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            if (preset.name == "Custom") Icons.Default.EditLocation
                            else Icons.Default.Place,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint     = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(preset.name, style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium)
                            if (preset.name != "Custom") {
                                Text("%.4f, %.4f".format(preset.lat, preset.lng),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            }
                        }
                    }
                    if (preset != SimPresets.ALL.last())
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PreviewDevTools() {
    AutoTripTheme { DevToolsScreen(rememberNavController()) }
}
