package com.example.autotrip.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.autotrip.location.LocationProvider
import com.example.autotrip.location.RealLocationProvider
import com.example.autotrip.location.SimulatedLocationProvider
import com.example.autotrip.model.Trip
import com.example.autotrip.repository.FirebaseAuthRepository
import com.example.autotrip.repository.TripRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ActiveTrackingViewModel : ViewModel() {

    private val authRepo = FirebaseAuthRepository()
    private val tripRepo = TripRepository()

    // ── Public observable state ───────────────────────────────────

    private val _distanceKm  = MutableStateFlow(0.0)
    val distanceKm: StateFlow<Double> = _distanceKm

    private val _speedKmh    = MutableStateFlow(0.0)
    val speedKmh: StateFlow<Double> = _speedKmh

    private val _routePoints = MutableStateFlow<List<Pair<Double, Double>>>(emptyList())
    val routePoints: StateFlow<List<Pair<Double, Double>>> = _routePoints

    private val _isSimulating = MutableStateFlow(false)
    val isSimulating: StateFlow<Boolean> = _isSimulating

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    sealed class SaveState {
        object Idle    : SaveState()
        object Saving  : SaveState()
        data class Saved(val tripId: String) : SaveState()
        data class Error(val message: String) : SaveState()
    }
    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState

    // ── Internal ─────────────────────────────────────────────────

    private var provider     : LocationProvider? = null
    private var lastLocation : android.location.Location? = null
    private var totalDistM   = 0.0
    private var startTimeMs  = 0L
    private var isSimMode    = false

    // ── Public API ───────────────────────────────────────────────

    fun startTracking(context: Context) {
        resetState()
        isSimMode           = false
        _isSimulating.value = false
        startWithProvider(RealLocationProvider(context))
    }

    fun startSimulation(provider: SimulatedLocationProvider) {
        resetState()
        isSimMode           = true
        _isSimulating.value = true
        _isLoading.value    = true
        startWithProvider(provider)
    }

    private fun startWithProvider(p: LocationProvider) {
        provider    = p
        startTimeMs = System.currentTimeMillis()
        p.startUpdates { loc -> onLocationReceived(loc) }
    }

    private fun onLocationReceived(loc: android.location.Location) {
        if (_isLoading.value) _isLoading.value = false

        lastLocation?.let { prev ->
            val delta = prev.distanceTo(loc)
            if (delta > 0.5f) {
                totalDistM       += delta
                _distanceKm.value = totalDistM / 1000.0
            }
        }
        lastLocation = loc

        if (loc.hasSpeed() && loc.speed > 0f) {
            _speedKmh.value = loc.speed * 3.6
        }

        _routePoints.value = _routePoints.value + Pair(loc.latitude, loc.longitude)
    }

    /**
     * [elapsedUiSecs] = seconds as counted by the UI timer (which runs at real time).
     * For simulation (10× speed), the REAL travel time = elapsedUiSecs × 10.
     * endTime is computed as startTime + realDurationMs so it's accurate.
     */
    fun stopTrackingAndSave(origin: String, destination: String, elapsedUiSecs: Int) {
        provider?.stopUpdates()
        provider = null

        val uid = authRepo.currentUser?.uid
        if (uid == null) { _saveState.value = SaveState.Error("Not logged in"); return }

        if (totalDistM < 100.0 && elapsedUiSecs < 30) {
            _saveState.value = SaveState.Error("Trip too short to record")
            return
        }

        val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        // For simulations the UI timer runs in real-time but movement is at 10×.
        // Real travel duration = elapsed * 10 for sim, elapsed * 1 for real.
        val realDurationSecs = if (isSimMode) elapsedUiSecs * 10 else elapsedUiSecs
        val endTimeMs        = startTimeMs + (realDurationSecs * 1000L)

        val trip = Trip(
            id           = "",
            origin       = origin,
            destination  = destination,
            startTime    = timeFmt.format(Date(startTimeMs)),
            endTime      = timeFmt.format(Date(endTimeMs)),
            travelMode   = "",
            purpose      = "",
            companions   = 0,
            cost         = 0.0,
            status       = "Needs Info",
            date         = dateFmt.format(Date(startTimeMs)),
            distanceKm   = totalDistM / 1000.0,
            durationSecs = realDurationSecs,
            routePoints  = _routePoints.value.map { (lat, lng) -> "$lat,$lng" }
        )

        viewModelScope.launch {
            _saveState.value = SaveState.Saving
            val result = tripRepo.saveTrip(uid, trip)
            _saveState.value = if (result.isSuccess)
                SaveState.Saved(result.getOrNull()!!)
            else
                SaveState.Error(result.exceptionOrNull()?.message ?: "Save failed")
        }
    }

    /** Discard — stop provider without saving anything. */
    fun discardTrip() {
        provider?.stopUpdates()
        provider = null
        resetState()
        _saveState.value = SaveState.Idle
    }

    private fun resetState() {
        lastLocation      = null
        totalDistM        = 0.0
        startTimeMs       = 0L
        _distanceKm.value = 0.0
        _speedKmh.value   = 0.0
        _routePoints.value = emptyList()
        _saveState.value  = SaveState.Idle
        _isLoading.value  = false
    }
}