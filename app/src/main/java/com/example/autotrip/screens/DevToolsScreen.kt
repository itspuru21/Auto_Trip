package com.example.autotrip.screens

import androidx.compose.animation.*
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.URL
import java.net.URLEncoder

// ─────────────────────────────────────────────────────────────────────────────
// DATA
// ─────────────────────────────────────────────────────────────────────────────

/** A geocoding result from Nominatim */
data class GeoResult(
    val displayName : String,
    val lat         : Double,
    val lng         : Double
)

/** How a location was chosen — preset from the list, address search, or raw coords */
sealed class LocationInput {
    data class Preset(val simPreset: SimPreset) : LocationInput()
    data class Geocoded(val result: GeoResult)  : LocationInput()
    data class Manual(val lat: Double, val lng: Double) : LocationInput()

    fun toSimPreset(fallbackName: String): SimPreset? = when (this) {
        is Preset   -> simPreset
        is Geocoded -> SimPreset(result.displayName.take(40), result.lat, result.lng)
        is Manual   -> SimPreset(fallbackName, lat, lng)
    }

    fun displayLabel(): String = when (this) {
        is Preset   -> simPreset.name
        is Geocoded -> result.displayName.take(50)
        is Manual   -> "%.5f, %.5f".format(lat, lng)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SCREEN
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun DevToolsScreen(
    navController : NavController,
    authViewModel : AuthViewModel? = null
) {
    var selectedMode  by remember { mutableStateOf(SimMode.CAR) }
    var originInput   by remember { mutableStateOf<LocationInput>(LocationInput.Preset(SimPresets.DEFAULT_ORIGIN)) }
    var destInput     by remember { mutableStateOf<LocationInput>(LocationInput.Preset(SimPresets.DEFAULT_DESTINATION)) }
    var showOriginPicker by remember { mutableStateOf(false) }
    var showDestPicker   by remember { mutableStateOf(false) }

    val resolvedOrigin = originInput.toSimPreset("Origin")
    val resolvedDest   = destInput.toSimPreset("Destination")
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

            // ── Debug banner ──────────────────────────────────────
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
                        "Debug only. Route follows real roads via OSRM at 10× speed.",
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
                                    style      = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
                Text(
                    "Simulated at 10× · Effective: ${(selectedMode.avgSpeedKmh * 10).toInt()} km/h",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            // ── Origin ────────────────────────────────────────────
            DevSection("Origin") {
                LocationPicker(
                    input        = originInput,
                    label        = "Origin",
                    onPickPreset = { showOriginPicker = true },
                    onSelect     = { originInput = it }
                )
            }

            // ── Destination ───────────────────────────────────────
            DevSection("Destination") {
                LocationPicker(
                    input        = destInput,
                    label        = "Destination",
                    onPickPreset = { showDestPicker = true },
                    onSelect     = { destInput = it }
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
                            "Route follows real roads via OSRM. " +
                                    "Falls back to straight-line if offline.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // ── Launch ────────────────────────────────────────────
            Button(
                onClick = {
                    val orig = resolvedOrigin ?: return@Button
                    val dest = resolvedDest   ?: return@Button
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
            onSelect  = { originInput = LocationInput.Preset(it); showOriginPicker = false },
            onDismiss = { showOriginPicker = false }
        )
    }
    if (showDestPicker) {
        PresetPickerDialog(
            title     = "Select Destination",
            onSelect  = { destInput = LocationInput.Preset(it); showDestPicker = false },
            onDismiss = { showDestPicker = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LOCATION PICKER
// Combines: preset button + address search + manual lat/lng
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LocationPicker(
    input        : LocationInput,
    label        : String,
    onPickPreset : () -> Unit,
    onSelect     : (LocationInput) -> Unit
) {
    // Which sub-mode is open
    var mode by remember { mutableStateOf("preset") }  // "preset" | "search" | "manual"

    // Search state
    var searchQuery   by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<GeoResult>>(emptyList()) }
    var isSearching   by remember { mutableStateOf(false) }
    var searchError   by remember { mutableStateOf<String?>(null) }

    // Manual state
    var manualLat by remember { mutableStateOf("") }
    var manualLng by remember { mutableStateOf("") }

    // Debounced Nominatim search
    LaunchedEffect(searchQuery) {
        if (searchQuery.length < 3) { searchResults = emptyList(); return@LaunchedEffect }
        delay(600)   // debounce
        isSearching = true
        searchError = null
        try {
            val results = nominatimSearch(searchQuery)
            searchResults = results
            if (results.isEmpty()) searchError = "No results found"
        } catch (e: Exception) {
            searchError = "Search failed — check internet"
            searchResults = emptyList()
        }
        isSearching = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

        // ── Current selection chip ────────────────────────────────
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Place, null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Text(
                    input.displayLabel(),
                    style    = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 2
                )
            }
        }

        // ── Mode toggle row ───────────────────────────────────────
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeChip("Presets",  Icons.Default.List,      mode == "preset", { mode = "preset"  })
            ModeChip("Search",   Icons.Default.Search,    mode == "search", { mode = "search"  })
            ModeChip("Lat/Lng",  Icons.Default.PinDrop,   mode == "manual", { mode = "manual"  })
        }

        // ── Preset mode ───────────────────────────────────────────
        AnimatedVisibility(visible = mode == "preset") {
            OutlinedButton(
                onClick  = onPickPreset,
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Place, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Choose from preset locations", modifier = Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(18.dp))
            }
        }

        // ── Search mode ───────────────────────────────────────────
        AnimatedVisibility(visible = mode == "search") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value         = searchQuery,
                    onValueChange = { searchQuery = it; searchResults = emptyList(); searchError = null },
                    label         = { Text("Search address or place") },
                    leadingIcon   = {
                        if (isSearching)
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        else
                            Icon(Icons.Default.Search, null)
                    },
                    trailingIcon  = if (searchQuery.isNotEmpty()) {{
                        IconButton(onClick = { searchQuery = ""; searchResults = emptyList() }) {
                            Icon(Icons.Default.Clear, null)
                        }
                    }} else null,
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true
                )

                // Error
                searchError?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 4.dp))
                }

                // Results dropdown
                if (searchResults.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        tonalElevation = 4.dp,
                        shadowElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            searchResults.forEachIndexed { idx, result ->
                                if (idx > 0) HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onSelect(LocationInput.Geocoded(result))
                                            searchQuery   = ""
                                            searchResults = emptyList()
                                            mode          = "preset"  // collapse back to chip view
                                        }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Default.LocationOn, null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            result.displayName,
                                            style    = MaterialTheme.typography.bodySmall,
                                            maxLines = 2
                                        )
                                        Text(
                                            "%.5f, %.5f".format(result.lat, result.lng),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Hint
                if (searchQuery.length in 1..2) {
                    Text("Type at least 3 characters to search",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(start = 4.dp))
                }
            }
        }

        // ── Manual lat/lng mode ───────────────────────────────────
        AnimatedVisibility(visible = mode == "manual") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value         = manualLat,
                        onValueChange = { manualLat = it },
                        label         = { Text("Latitude") },
                        modifier      = Modifier.weight(1f),
                        singleLine    = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value         = manualLng,
                        onValueChange = { manualLng = it },
                        label         = { Text("Longitude") },
                        modifier      = Modifier.weight(1f),
                        singleLine    = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
                val lat = manualLat.toDoubleOrNull()
                val lng = manualLng.toDoubleOrNull()
                OutlinedButton(
                    onClick  = {
                        if (lat != null && lng != null) {
                            onSelect(LocationInput.Manual(lat, lng))
                            mode = "preset"
                        }
                    },
                    enabled  = lat != null && lng != null,
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Use these coordinates")
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// NOMINATIM GEOCODING
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Calls the free Nominatim API (OpenStreetMap geocoding).
 * No API key required. Returns up to 5 results.
 * Must be called from a coroutine — does network I/O on the calling dispatcher.
 */
private suspend fun nominatimSearch(query: String): List<GeoResult> =
    withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url     = "https://nominatim.openstreetmap.org/search" +
                "?q=$encoded&format=json&limit=5&addressdetails=0"

        val json = URL(url).openConnection().apply {
            // Nominatim requires a User-Agent header
            setRequestProperty("User-Agent", "AutoTripApp/1.0")
            connectTimeout = 8_000
            readTimeout    = 8_000
        }.getInputStream().bufferedReader().readText()

        val array = JSONArray(json)
        (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            GeoResult(
                displayName = obj.getString("display_name"),
                lat         = obj.getDouble("lat"),
                lng         = obj.getDouble("lon")
            )
        }
    }

// ─────────────────────────────────────────────────────────────────────────────
// SMALL HELPERS
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ModeChip(
    label    : String,
    icon     : androidx.compose.ui.graphics.vector.ImageVector,
    selected : Boolean,
    onClick  : () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(20.dp)
            ),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, null,
                modifier = Modifier.size(14.dp),
                tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label,
                style      = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color      = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DevSection(
    title   : String,
    content : @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title,
            style      = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color      = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewDevToolsScreen() {
    AutoTripTheme { DevToolsScreen(rememberNavController()) }
}