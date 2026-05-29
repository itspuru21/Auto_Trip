package com.example.autotrip.location

import android.location.Location
import android.os.SystemClock
import com.example.autotrip.simulation.SimMode
import com.example.autotrip.simulation.SimPreset
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.*

/**
 * Simulates GPS movement along a REAL road route fetched from OSRM.
 * Tries multiple OSRM public endpoints before falling back to straight-line.
 *
 * Self-contained coroutine scope — never leaks into any ViewModel.
 * All callbacks are dispatched on Main so StateFlow updates are safe.
 */
class SimulatedLocationProvider(
    private val origin      : SimPreset,
    private val destination : SimPreset,
    private val mode        : SimMode
) : LocationProvider {

    companion object {
        private const val SPEED_MULTIPLIER = 10.0
        private const val TICK_MS          = 500L
        private const val WAYPOINT_SPACING = 10.0   // metres between densified points
        private const val CONNECT_TIMEOUT  = 8_000   // ms
        private const val READ_TIMEOUT     = 10_000  // ms

        // Multiple public OSRM endpoints — tries each in order
        private val OSRM_ENDPOINTS = listOf(
            "https://router.project-osrm.org/route/v1/driving",
            "https://routing.openstreetmap.de/routed-car/route/v1/driving"
        )
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun startUpdates(onLocation: (Location) -> Unit) {
        scope.launch {
            val waypoints = fetchOsrmRouteWithRetry() ?: buildStraightLine()
            emitWaypoints(waypoints, onLocation)
        }
    }

    override fun stopUpdates() { scope.cancel() }

    // ── OSRM real road route — tries multiple endpoints ───────────

    private fun fetchOsrmRouteWithRetry(): List<Pair<Double, Double>>? {
        for (baseUrl in OSRM_ENDPOINTS) {
            try {
                val result = fetchFromEndpoint(baseUrl)
                if (result != null && result.size > 2) return result
            } catch (_: Exception) {
                // Try next endpoint
            }
        }
        return null
    }

    private fun fetchFromEndpoint(baseUrl: String): List<Pair<Double, Double>>? {
        val urlString = "$baseUrl/${origin.lng},${origin.lat};" +
                "${destination.lng},${destination.lat}" +
                "?overview=full&geometries=geojson&steps=false"

        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT
        connection.readTimeout    = READ_TIMEOUT
        connection.requestMethod  = "GET"
        connection.setRequestProperty("User-Agent", "AutoTripApp/1.0")

        return try {
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) return null

            val text = BufferedReader(InputStreamReader(connection.inputStream))
                .use { it.readText() }

            val root = JSONObject(text)
            if (root.getString("code") != "Ok") return null

            val coords = root
                .getJSONArray("routes").getJSONObject(0)
                .getJSONObject("geometry")
                .getJSONArray("coordinates")   // each: [lng, lat]

            val raw = (0 until coords.length()).map { i ->
                val pt = coords.getJSONArray(i)
                Pair(pt.getDouble(1), pt.getDouble(0))  // → (lat, lng)
            }

            if (raw.size < 2) null else densify(raw, WAYPOINT_SPACING)
        } finally {
            connection.disconnect()
        }
    }

    /** Insert evenly-spaced intermediate points so every tick is ~10 m. */
    private fun densify(
        points     : List<Pair<Double, Double>>,
        stepMetres : Double
    ): List<Pair<Double, Double>> {
        if (points.size < 2) return points
        val out = mutableListOf(points.first())
        for (i in 1 until points.size) {
            val (lat1, lng1) = points[i - 1]
            val (lat2, lng2) = points[i]
            val dist  = haversineM(lat1, lng1, lat2, lng2)
            val steps = (dist / stepMetres).toInt()
            for (s in 1..steps) {
                val t = s.toDouble() / (steps + 1)
                out.add(Pair(lerp(lat1, lat2, t), lerp(lng1, lng2, t)))
            }
            out.add(points[i])
        }
        return out
    }

    // ── Straight-line fallback ────────────────────────────────────

    private fun buildStraightLine(): List<Pair<Double, Double>> {
        val dist  = haversineM(origin.lat, origin.lng, destination.lat, destination.lng)
        val steps = (dist / WAYPOINT_SPACING).toInt().coerceAtLeast(50)
        return (0..steps).map { i ->
            val t = i.toDouble() / steps
            Pair(lerp(origin.lat, destination.lat, t), lerp(origin.lng, destination.lng, t))
        }
    }

    // ── Emit loop ─────────────────────────────────────────────────

    private suspend fun emitWaypoints(
        waypoints  : List<Pair<Double, Double>>,
        onLocation : (Location) -> Unit
    ) {
        val realSpeedMps = mode.avgSpeedKmh / 3.6
        val simSpeedMps  = realSpeedMps * SPEED_MULTIPLIER
        val tickSec      = TICK_MS / 1000.0
        val step = ((simSpeedMps * tickSec) / WAYPOINT_SPACING).toInt().coerceAtLeast(1)

        var idx = 0
        while (idx < waypoints.size && scope.isActive) {
            val (lat, lng) = waypoints[idx]
            val nextIdx    = (idx + 1).coerceAtMost(waypoints.size - 1)
            val bearing    = if (nextIdx != idx)
                bearingDeg(lat, lng, waypoints[nextIdx].first, waypoints[nextIdx].second)
            else 0.0

            val loc = Location("simulation").apply {
                latitude             = lat
                longitude            = lng
                speed                = realSpeedMps.toFloat()
                this.bearing         = bearing.toFloat()
                accuracy             = 4f
                time                 = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            }
            withContext(Dispatchers.Main) { onLocation(loc) }
            delay(TICK_MS)
            idx += step
        }

        // Final fix at exact destination
        val finalLoc = Location("simulation").apply {
            latitude             = destination.lat
            longitude            = destination.lng
            speed                = 0f
            accuracy             = 4f
            time                 = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
        withContext(Dispatchers.Main) { onLocation(finalLoc) }
    }

    // ── Geometry helpers ─────────────────────────────────────────

    private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t

    private fun haversineM(lat1: Double, lon1: Double,
                           lat2: Double, lon2: Double): Double {
        val r    = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a    = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun bearingDeg(lat1: Double, lon1: Double,
                           lat2: Double, lon2: Double): Double {
        val dLon = Math.toRadians(lon2 - lon1)
        val φ1   = Math.toRadians(lat1)
        val φ2   = Math.toRadians(lat2)
        val y    = sin(dLon) * cos(φ2)
        val x    = cos(φ1) * sin(φ2) - sin(φ1) * cos(φ2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }
}