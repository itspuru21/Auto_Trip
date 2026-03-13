package com.example.autotrip.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.autotrip.components.AutoTripTopBar
import com.example.autotrip.simulation.SimMode
import com.example.autotrip.simulation.SimPreset
import com.example.autotrip.ui.theme.AutoTripTheme
import com.example.autotrip.viewmodel.AuthViewModel
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.net.URLEncoder

// ─────────────────────────────────────────────────────────────────────────────
// STATE
// ─────────────────────────────────────────────────────────────────────────────

private enum class PinMode { ORIGIN, DESTINATION, DONE }

// ─────────────────────────────────────────────────────────────────────────────
// SCREEN
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun DevToolsScreen(
    navController : NavController,
    authViewModel : AuthViewModel? = null
) {
    var selectedMode  by remember { mutableStateOf(SimMode.CAR) }
    var originPoint   by remember { mutableStateOf<GeoPoint?>(null) }
    var destPoint     by remember { mutableStateOf<GeoPoint?>(null) }
    var pinMode       by remember { mutableStateOf(PinMode.ORIGIN) }
    var originName    by remember { mutableStateOf("") }
    var destName      by remember { mutableStateOf("") }

    val canLaunch = originPoint != null && destPoint != null

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
        ) {
            InstructionBanner(
                pinMode = pinMode,
                origin  = originPoint,
                dest    = destPoint
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                MapPickerView(
                    origin   = originPoint,
                    dest     = destPoint,
                    onTap    = { tappedPoint ->
                        when (pinMode) {
                            PinMode.ORIGIN -> {
                                originPoint = tappedPoint
                                if (originName.isBlank())
                                    originName = "Start %.4f,%.4f".format(
                                        tappedPoint.latitude, tappedPoint.longitude)
                                pinMode = PinMode.DESTINATION
                            }
                            PinMode.DESTINATION -> {
                                destPoint = tappedPoint
                                if (destName.isBlank())
                                    destName = "End %.4f,%.4f".format(
                                        tappedPoint.latitude, tappedPoint.longitude)
                                pinMode = PinMode.DONE
                            }
                            PinMode.DONE -> { /* use Reset to re-place */ }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (pinMode != PinMode.DONE) {
                    Icon(
                        Icons.Default.AddCircleOutline,
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.Center).size(36.dp),
                        tint = if (pinMode == PinMode.ORIGIN) Color(0xFF2E7D32)
                        else Color(0xFFD32F2F)
                    )
                }

                if (originPoint != null || destPoint != null) {
                    SmallFloatingActionButton(
                        onClick = {
                            originPoint = null; destPoint = null
                            originName  = "";   destName  = ""
                            pinMode     = PinMode.ORIGIN
                        },
                        modifier       = Modifier.align(Alignment.TopEnd).padding(10.dp),
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor   = MaterialTheme.colorScheme.onErrorContainer
                    ) {
                        Icon(Icons.Default.Refresh, "Reset", modifier = Modifier.size(18.dp))
                    }
                }
            }

            BottomPanel(
                selectedMode = selectedMode,
                onModeSelect = { selectedMode = it },
                origin       = originPoint,
                dest         = destPoint,
                originName   = originName,
                destName     = destName,
                onOriginName = { originName = it },
                onDestName   = { destName = it },
                canLaunch    = canLaunch,
                onLaunch     = {
                    val orig = originPoint ?: return@BottomPanel
                    val dst  = destPoint   ?: return@BottomPanel
                    val oName = originName.ifBlank {
                        "Start %.4f,%.4f".format(orig.latitude, orig.longitude)
                    }
                    val dName = destName.ifBlank {
                        "End %.4f,%.4f".format(dst.latitude, dst.longitude)
                    }
                    navController.navigate(
                        "active_tracking_sim" +
                                "/${URLEncoder.encode(oName, "UTF-8")}" +
                                "/${orig.latitude}/${orig.longitude}" +
                                "/${URLEncoder.encode(dName, "UTF-8")}" +
                                "/${dst.latitude}/${dst.longitude}" +
                                "/${selectedMode.name}"
                    )
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// INSTRUCTION BANNER
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun InstructionBanner(
    pinMode : PinMode,
    origin  : GeoPoint?,
    dest    : GeoPoint?
) {
    val bgColor = when (pinMode) {
        PinMode.ORIGIN      -> Color(0xFF2E7D32).copy(alpha = 0.10f)
        PinMode.DESTINATION -> Color(0xFFD32F2F).copy(alpha = 0.10f)
        PinMode.DONE        -> Color(0xFFFF6F00).copy(alpha = 0.10f)
    }
    val icon = when (pinMode) {
        PinMode.ORIGIN      -> Icons.Default.LocationOn
        PinMode.DESTINATION -> Icons.Default.Flag
        PinMode.DONE        -> Icons.Default.CheckCircle
    }
    val iconTint = when (pinMode) {
        PinMode.ORIGIN      -> Color(0xFF2E7D32)
        PinMode.DESTINATION -> Color(0xFFD32F2F)
        PinMode.DONE        -> Color(0xFFFF6F00)
    }
    val message = when (pinMode) {
        PinMode.ORIGIN      -> "Tap anywhere on the map to set your START point"
        PinMode.DESTINATION -> "Now tap to set your END (destination) point"
        PinMode.DONE        -> "Both pins set — name them, choose transport mode and launch!"
    }

    Surface(color = bgColor, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier              = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StepDot(number = 1, done = origin != null, active = pinMode == PinMode.ORIGIN,  color = Color(0xFF2E7D32))
            StepDot(number = 2, done = dest != null,   active = pinMode == PinMode.DESTINATION, color = Color(0xFFD32F2F))
            Spacer(Modifier.width(2.dp))
            Icon(icon, null, modifier = Modifier.size(18.dp), tint = iconTint)
            Text(message, style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun StepDot(number: Int, done: Boolean, active: Boolean, color: Color) {
    val bg     = when { done -> color; active -> color.copy(alpha = 0.18f); else -> Color.Gray.copy(0.13f) }
    val border = if (active || done) color else Color.Gray.copy(alpha = 0.25f)
    Box(
        modifier         = Modifier.size(22.dp).background(bg, CircleShape).border(1.5.dp, border, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (done) Icon(Icons.Default.Check, null, modifier = Modifier.size(12.dp), tint = Color.White)
        else Text("$number", style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold, color = if (active) color else Color.Gray)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MAP PICKER — green start pin, RED end pin (not both green)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MapPickerView(
    origin   : GeoPoint?,
    dest     : GeoPoint?,
    onTap    : (GeoPoint) -> Unit,
    modifier : Modifier = Modifier
) {
    val context = LocalContext.current

    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(14.0)
            controller.setCenter(GeoPoint(19.8762, 75.3433))
        }
    }

    val originMarker = remember { Marker(mapView) }
    val destMarker   = remember { Marker(mapView) }
    val routeLine    = remember { Polyline().apply {
        outlinePaint.color       = android.graphics.Color.parseColor("#1565C0")
        outlinePaint.strokeWidth = 5f
        outlinePaint.pathEffect  = android.graphics.DashPathEffect(floatArrayOf(20f, 12f), 0f)
    }}

    LaunchedEffect(origin, dest) {
        mapView.overlays.clear()

        val receiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean { onTap(p); return true }
            override fun longPressHelper(p: GeoPoint): Boolean = false
        }
        mapView.overlays.add(MapEventsOverlay(receiver))

        origin?.let { pt ->
            originMarker.apply {
                position = pt
                title    = "Start"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                // Green tint for start
                icon = context.getDrawable(org.osmdroid.library.R.drawable.marker_default)
                    ?.apply { setTint(android.graphics.Color.parseColor("#2E7D32")) }
            }
            mapView.overlays.add(originMarker)
        }

        dest?.let { pt ->
            destMarker.apply {
                position = pt
                title    = "End"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                // Red tint for end
                icon = context.getDrawable(org.osmdroid.library.R.drawable.marker_default)
                    ?.apply { setTint(android.graphics.Color.parseColor("#D32F2F")) }
            }
            mapView.overlays.add(destMarker)
        }

        if (origin != null && dest != null) {
            routeLine.setPoints(listOf(origin, dest))
            mapView.overlays.add(routeLine)
        }

        mapView.invalidate()
    }

    AndroidView(factory = { mapView }, modifier = modifier)

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose { mapView.onPause() }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// BOTTOM PANEL
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BottomPanel(
    selectedMode : SimMode,
    onModeSelect : (SimMode) -> Unit,
    origin       : GeoPoint?,
    dest         : GeoPoint?,
    originName   : String,
    destName     : String,
    onOriginName : (String) -> Unit,
    onDestName   : (String) -> Unit,
    canLaunch    : Boolean,
    onLaunch     : () -> Unit
) {
    Surface(tonalElevation = 8.dp, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier            = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── Coordinate chips ──────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CoordChip(label = "Start", point = origin, color = Color(0xFF2E7D32), modifier = Modifier.weight(1f))
                CoordChip(label = "End",   point = dest,   color = Color(0xFFD32F2F), modifier = Modifier.weight(1f))
            }

            // ── Custom name fields (shown once both pins are placed) ──
            AnimatedVisibility(visible = canLaunch) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value         = originName,
                        onValueChange = onOriginName,
                        label         = { Text("Start point name") },
                        leadingIcon   = { Icon(Icons.Default.LocationOn, null, tint = Color(0xFF2E7D32)) },
                        singleLine    = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier      = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value         = destName,
                        onValueChange = onDestName,
                        label         = { Text("End point name") },
                        leadingIcon   = { Icon(Icons.Default.Flag, null, tint = Color(0xFFD32F2F)) },
                        singleLine    = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier      = Modifier.fillMaxWidth()
                    )
                }
            }

            // ── Transport mode row (no Metro) ─────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SimMode.entries.forEach { mode ->
                    val selected = mode == selectedMode
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onModeSelect(mode) }
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) Color(0xFFFF6F00)
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(10.dp)
                            ),
                        color = if (selected) Color(0xFFFF6F00).copy(alpha = 0.10f)
                        else MaterialTheme.colorScheme.surface
                    ) {
                        Column(
                            modifier            = Modifier.padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(mode.emoji, fontSize = 16.sp)
                            Text(
                                mode.label.split("-").first().split(" ").first(),
                                style      = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                maxLines   = 1
                            )
                        }
                    }
                }
            }

            // ── Launch button ─────────────────────────────────────
            Button(
                onClick  = onLaunch,
                enabled  = canLaunch,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (canLaunch) "Launch Simulation" else "Tap map to set start & end",
                    color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
private fun CoordChip(
    label    : String,
    point    : GeoPoint?,
    color    : Color,
    modifier : Modifier = Modifier
) {
    val isSet = point != null
    Surface(
        shape    = RoundedCornerShape(10.dp),
        color    = if (isSet) color.copy(alpha = 0.09f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier.border(
            width = 1.dp,
            color = if (isSet) color.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
            shape = RoundedCornerShape(10.dp)
        )
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                if (isSet) Icons.Default.LocationOn else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint     = if (isSet) color else Color.Gray
            )
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold, color = if (isSet) color else Color.Gray)
                Text(
                    if (isSet) "%.4f, %.4f".format(point!!.latitude, point.longitude)
                    else "tap map to set",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSet) MaterialTheme.colorScheme.onSurface else Color.Gray.copy(alpha = 0.6f)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PREVIEW
// ─────────────────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
fun PreviewDevToolsScreen() {
    AutoTripTheme { DevToolsScreen(rememberNavController()) }
}