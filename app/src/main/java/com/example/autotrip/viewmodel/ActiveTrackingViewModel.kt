package com.example.autotrip.viewmodel

import android.content.Context
import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.autotrip.location.LocationProvider
import com.example.autotrip.location.RealLocationProvider
import com.example.autotrip.model.Trip
import com.example.autotrip.repository.FirebaseAuthRepository
import com.example.autotrip.repository.TripRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manages an active trip tracking session.
 *
 * Depends on [LocationProvider] — swap [RealLocationProvider] for
 * [SimulatedLocationProvider] during development without touching this class.
 */
class ActiveTrackingViewModel : ViewModel() {

    private val authRepo = FirebaseAuthRepository()
    private val tripRepo = TripRepository()

    // ── Live tracking state ──────────────────────────────────────

    private val _distanceKm = MutableStateFlow(0.0)
    val distanceKm: StateFlow<Double> = _distanceKm

    private val _speedKmh = MutableStateFlow(0.0)
    val speedKmh: StateFlow<Double> = _speedKmh

    private val _isSimulating = MutableStateFlow(false)
    val isSimulating: StateFlow<Boolean> = _isSimulating

    /** Live route breadcrumbs for the map polyline — emitted on every GPS fix. */
    private val _routePoints = MutableStateFlow<List<Pair<Double, Double>>>(emptyList())
    val routePoints: StateFlow<List<Pair<Double, Double>>> = _routePoints

    // ── Save state ───────────────────────────────────────────────

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
    private var lastLocation : Location? = null
    private var totalDistM   = 0.0
    private var startTimeMs  = 0L   // set in startWithProvider, cleared in resetState

    // ── Start — real GPS ─────────────────────────────────────────

    fun startTracking(context: Context) {
        resetState()
        _isSimulating.value = false
        startWithProvider(RealLocationProvider(context))
    }

    // ── Start — simulated GPS ────────────────────────────────────

    fun startSimulation(simProvider: LocationProvider) {
        resetState()
        _isSimulating.value = true
        startWithProvider(simProvider)
    }

    private fun startWithProvider(p: LocationProvider) {
        provider    = p
        // FIX: capture start time HERE, after resetState() cleared it,
        // so every trip gets its own correct start timestamp.
        startTimeMs = System.currentTimeMillis()
        p.startUpdates { loc -> onLocationReceived(loc) }
    }

    private fun onLocationReceived(loc: Location) {
        // Accumulate distance — use 0.5 m threshold so even slow/simulated
        // movement is captured without noise from GPS jitter.
        lastLocation?.let { prev ->
            val delta = prev.distanceTo(loc)
            if (delta > 0.5f) {
                totalDistM        += delta
                _distanceKm.value  = totalDistM / 1000.0
            }
        }
        lastLocation = loc

        // Speed — loc.speed is in m/s.  hasSpeed() returns true when the
        // provider explicitly sets the speed field (real GPS and our simulator
        // both do this).  Fall back to keeping the last known value.
        if (loc.hasSpeed() && loc.speed >= 0f) {
            _speedKmh.value = loc.speed * 3.6
        }

        // Append breadcrumb for live map polyline
        val current = _routePoints.value.toMutableList()
        current.add(Pair(loc.latitude, loc.longitude))
        _routePoints.value = current
    }

    // ── Stop & save ──────────────────────────────────────────────

    fun stopTrackingAndSave(origin: String, destination: String, durationSecs: Int) {
        provider?.stopUpdates()
        provider = null

        val uid = authRepo.currentUser?.uid
        if (uid == null) { _saveState.value = SaveState.Error("Not logged in"); return }

        if (totalDistM < 100.0 && durationSecs < 30) {
            _saveState.value = SaveState.Error("Trip too short to record")
            return
        }

        val now     = System.currentTimeMillis()
        val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        // Convert route points to storable strings "lat,lng"
        val routeStrings = _routePoints.value.map { (lat, lng) ->
            "$lat,$lng"
        }

        val trip = Trip(
            id          = "",
            origin      = origin,
            destination = destination,
            // FIX: startTimeMs was captured at trip start, not at ViewModel init,
            // so this is always the correct per-trip start time.
            startTime   = timeFmt.format(Date(startTimeMs)),
            endTime     = timeFmt.format(Date(now)),
            travelMode  = "",
            purpose     = "",
            companions  = 0,
            cost        = 0.0,
            status      = "Needs Info",
            date        = dateFmt.format(Date(now)),
            distanceKm  = totalDistM / 1000.0,
            routePoints = routeStrings
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

    // ── Cleanup ──────────────────────────────────────────────────

    private fun resetState() {
        totalDistM    = 0.0
        startTimeMs   = 0L          // FIX: also reset start time so old value never bleeds over
        lastLocation  = null
        _distanceKm.value   = 0.0
        _speedKmh.value     = 0.0
        _routePoints.value  = emptyList()
        _saveState.value    = SaveState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        provider?.stopUpdates()
    }

    fun resetSaveState() { _saveState.value = SaveState.Idle }
}