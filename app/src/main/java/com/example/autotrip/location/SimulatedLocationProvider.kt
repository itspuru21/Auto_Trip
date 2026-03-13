package com.example.autotrip.location

import android.location.Location
import android.os.SystemClock
import com.example.autotrip.simulation.SimMode
import com.example.autotrip.simulation.SimPreset
import kotlinx.coroutines.*
import kotlin.math.*

/**
 * Development-only simulated GPS provider.
 *
 * Interpolates a straight-line route between [origin] and [destination]
 * emitting fake [Location] objects at the average speed of [mode].
 *
 * Speed multiplier is fixed at 10× — a 20-minute car trip completes
 * in ~2 minutes of real time during testing.
 */
class SimulatedLocationProvider(
    private val origin      : SimPreset,
    private val destination : SimPreset,
    private val mode        : SimMode
) : LocationProvider {

    companion object {
        private const val SPEED_MULTIPLIER = 10.0
        private const val TICK_MS          = 1_000L
        private const val NOISE_DEG        = 0.000015   // ≈ 1.5 m
    }

    private var job: Job? = null

    override fun startUpdates(onLocation: (Location) -> Unit) {
        job = CoroutineScope(Dispatchers.Main).launch {
            val totalDistanceM = haversineMetres(
                origin.lat, origin.lng, destination.lat, destination.lng
            )

            // metres per real second (simulated at 10×)
            val speedMps   = (mode.avgSpeedKmh / 3.6) * SPEED_MULTIPLIER
            val totalTicks = (totalDistanceM / speedMps).toInt().coerceAtLeast(2)

            // Bearing from origin → destination (constant for straight-line route)
            val bearing = bearingDeg(origin.lat, origin.lng, destination.lat, destination.lng)

            for (tick in 0..totalTicks) {
                val fraction = (tick.toDouble() / totalTicks).coerceIn(0.0, 1.0)

                val lat = lerp(origin.lat, destination.lat, fraction) + noise()
                val lng = lerp(origin.lng, destination.lng, fraction) + noise()

                val loc = Location("simulation").apply {
                    latitude             = lat
                    longitude            = lng
                    // Report real-world speed (not simulated speed) so the
                    // ViewModel displays a realistic km/h value on screen.
                    speed                = (speedMps / SPEED_MULTIPLIER).toFloat()
                    // FIX: setting bearing causes hasSpeed() to return true on
                    // synthetic Location objects — without this the ViewModel
                    // ignores the speed field entirely.
                    this.bearing         = bearing.toFloat()
                    accuracy             = 4f
                    time                 = System.currentTimeMillis()
                    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                }
                onLocation(loc)
                if (tick < totalTicks) delay(TICK_MS)
            }

            // Final fix — exact destination, speed zero
            val finalLoc = Location("simulation").apply {
                latitude             = destination.lat
                longitude            = destination.lng
                speed                = 0f
                this.bearing         = bearing.toFloat()
                accuracy             = 4f
                time                 = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            }
            onLocation(finalLoc)
        }
    }

    override fun stopUpdates() {
        job?.cancel()
        job = null
    }

    // ── Math helpers ─────────────────────────────────────────────

    private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t

    private fun noise() = (Math.random() - 0.5) * 2 * NOISE_DEG

    private fun haversineMetres(lat1: Double, lon1: Double,
                                lat2: Double, lon2: Double): Double {
        val r    = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a    = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Initial bearing from point 1 → point 2 in degrees [0, 360). */
    private fun bearingDeg(lat1: Double, lon1: Double,
                           lat2: Double, lon2: Double): Double {
        val dLon = Math.toRadians(lon2 - lon1)
        val rlat1 = Math.toRadians(lat1)
        val rlat2 = Math.toRadians(lat2)
        val y = sin(dLon) * cos(rlat2)
        val x = cos(rlat1) * sin(rlat2) - sin(rlat1) * cos(rlat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }
}