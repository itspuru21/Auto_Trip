package com.example.autotrip.viewmodel

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.autotrip.model.Trip
import com.example.autotrip.prefs.DataSharingPrefs
import com.example.autotrip.repository.FirebaseAuthRepository
import com.example.autotrip.repository.TripRepository
import com.example.autotrip.service.TrackingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * ViewModel for both real GPS tracking and simulation.
 *
 * Real tracking: delegates entirely to [TrackingService] (foreground service),
 * binding to it so the UI gets live updates even after the user returns from
 * the background. The service survives process-backgrounding; the ViewModel just
 * mirrors its state.
 *
 * Simulation: unchanged — the SimulatedLocationProvider runs inside the ViewModel
 * (simulations don't need background survival).
 *
 * Data-sharing gate:
 * Before saving ANY trip to Firestore, the ViewModel checks [DataSharingPrefs].
 * If sharing is disabled the trip is NOT written to Firebase. The UI layer
 * (ActiveTrackingScreen) is responsible for blocking the user from reaching the
 * tracking phase at all — this is a secondary safety net.
 */
class ActiveTrackingViewModel : ViewModel() {

    private val authRepo = FirebaseAuthRepository()
    private val tripRepo = TripRepository()

    // ── Public observable state ───────────────────────────────────

    private val _distanceKm  = MutableStateFlow(0.0)
    val distanceKm: StateFlow<Double> = _distanceKm

    private val _speedKmh = MutableStateFlow(0.0)
    val speedKmh: StateFlow<Double> = _speedKmh

    private val _routePoints = MutableStateFlow<List<Pair<Double, Double>>>(emptyList())
    val routePoints: StateFlow<List<Pair<Double, Double>>> = _routePoints

    private val _isSimulating = MutableStateFlow(false)
    val isSimulating: StateFlow<Boolean> = _isSimulating

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    /** True while the service is bound and tracking is active */
    private val _isTrackingActive = MutableStateFlow(false)
    val isTrackingActive: StateFlow<Boolean> = _isTrackingActive

    sealed class SaveState {
        object Idle   : SaveState()
        object Saving : SaveState()
        class Saved(val tripId: String) : SaveState()
        class Error(val message: String) : SaveState()
    }

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState

    // ── Service binding ───────────────────────────────────────────

    private var boundService  : TrackingService? = null
    private var appContext    : Context?          = null
    private var isBound       = false

    private val stateListener: (TrackingService.TrackingState) -> Unit = { state ->
        _distanceKm.value       = state.distanceKm
        _speedKmh.value         = state.speedKmh
        _routePoints.value      = state.routePoints
        _isTrackingActive.value = state.isTracking
        _isLoading.value        = state.routePoints.isEmpty() && state.isTracking
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val service = (binder as TrackingService.LocalBinder).service
            boundService = service
            service.addListener(stateListener)
            stateListener(service.state)
            isBound = true
        }
        override fun onServiceDisconnected(name: ComponentName) {
            boundService?.removeListener(stateListener)
            boundService = null
            isBound      = false
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Real GPS tracking — via foreground service
    // ─────────────────────────────────────────────────────────────

    fun startTracking(context: Context, origin: String = "", destination: String = "") {
        appContext = context.applicationContext
        resetState()
        _isSimulating.value = false
        _isLoading.value    = true

        val intent = Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_START
            putExtra(TrackingService.EXTRA_ORIGIN,      origin)
            putExtra(TrackingService.EXTRA_DESTINATION, destination)
        }
        context.startForegroundService(intent)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun bindToRunningService(context: Context) {
        if (isBound) return
        appContext = context.applicationContext
        val intent = Intent(context, TrackingService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun unbindFromService(context: Context) {
        if (isBound) {
            boundService?.removeListener(stateListener)
            context.unbindService(connection)
            isBound = false
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Simulation
    // ─────────────────────────────────────────────────────────────

    private val legacyProvider = com.example.autotrip.location.LegacyProviderHolder()

    fun startSimulation(
        provider  : com.example.autotrip.location.SimulatedLocationProvider,
        speedKmh  : Double = 0.0
    ) {
        resetState()
        _isSimulating.value = true
        _isLoading.value    = true
        legacyProvider.simSpeedKmh  = speedKmh
        legacyProvider.startTimeMs  = System.currentTimeMillis()
        legacyProvider.provider     = provider
        provider.startUpdates { loc ->
            if (legacyProvider.isLoading) legacyProvider.isLoading = false
            legacyProvider.lastLocation?.let { prev ->
                val delta = prev.distanceTo(loc)
                if (delta > 0.5f) {
                    legacyProvider.totalDistM += delta
                    _distanceKm.value          = legacyProvider.totalDistM / 1000.0
                }
            }
            legacyProvider.lastLocation = loc
            if (loc.hasSpeed() && loc.speed > 0f) _speedKmh.value = loc.speed * 3.6
            _routePoints.value = _routePoints.value + Pair(loc.latitude, loc.longitude)
            if (_isLoading.value) _isLoading.value = false
        }
    }

    // ─────────────────────────────────────────────────────────────
    // End trip
    // ─────────────────────────────────────────────────────────────

    fun stopTrackingAndSave(origin: String, destination: String, elapsedUiSecs: Int) {
        if (_isSimulating.value) {
            stopSimulationAndSave(origin, destination, elapsedUiSecs)
        } else {
            stopServiceAndSave(origin, destination, elapsedUiSecs)
        }
    }

    private fun stopServiceAndSave(origin: String, destination: String, elapsedUiSecs: Int) {
        val ctx = appContext ?: return

        val finalState = boundService?.stopTracking()

        if (isBound) {
            try { ctx.unbindService(connection) } catch (_: Exception) {}
            isBound = false
        }

        val routePts   = finalState?.routePoints ?: _routePoints.value
        val distKm     = finalState?.distanceKm  ?: _distanceKm.value
        val durationS  = finalState?.elapsedSecs ?: elapsedUiSecs

        saveTrip(origin, destination, distKm, durationS, routePts, isSimMode = false)
    }

    private fun stopSimulationAndSave(origin: String, destination: String, elapsedUiSecs: Int) {
        val p = legacyProvider.provider
        legacyProvider.provider = null
        p?.stopUpdates()

        val distKm    = legacyProvider.totalDistM / 1000.0
        val simSpeed  = legacyProvider.simSpeedKmh
        val durationS = if (simSpeed > 0.0) {
            ((distKm / simSpeed) * 3600).toInt()
        } else elapsedUiSecs

        saveTrip(origin, destination, distKm, durationS, _routePoints.value, isSimMode = true)
    }

    /**
     * Saves the trip.
     *
     * DATA-SHARING GATE: If [DataSharingPrefs.isTripSharingEnabled] returns false
     * the trip is NOT written to Firestore. This is a secondary safeguard — the UI
     * should have prevented the user from starting a trip in the first place.
     * We still emit [SaveState.Saved] with a local placeholder ID so the UI flow
     * (navigating to TripDetailsScreen) is not broken.
     *
     * Note: because the trip has no Firestore ID in the blocked case, the details
     * screen will show a skeleton and offer no save option — which is the correct
     * behaviour since there's nothing to save.
     */
    private fun saveTrip(
        origin      : String,
        destination : String,
        distKm      : Double,
        durationS   : Int,
        routePoints : List<Pair<Double, Double>>,
        isSimMode   : Boolean
    ) {
        val ctx = appContext
        val uid = authRepo.currentUser?.uid
        if (uid == null) { _saveState.value = SaveState.Error("Not logged in"); return }

        // ── Data-sharing check ────────────────────────────────────
        if (ctx != null && !DataSharingPrefs.isTripSharingEnabled(ctx)) {
            // Sharing is off — do not write to Firestore.
            // Emit an error state so the UI can surface a meaningful message.
            _saveState.value = SaveState.Error(
                "Trip data sharing is disabled. Enable it in Profile → Settings to record trips."
            )
            return
        }

        val startMs = if (isSimMode) legacyProvider.startTimeMs else
            (System.currentTimeMillis() - durationS * 1000L)

        val timeFmt   = SimpleDateFormat("h:mm a",    Locale.getDefault())
        val dateFmt   = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val endTimeMs = startMs + (durationS * 1000L)

        val trip = Trip(
            origin       = origin,
            destination  = destination,
            startTime    = timeFmt.format(Date(startMs)),
            endTime      = timeFmt.format(Date(endTimeMs)),
            travelMode   = "",
            purpose      = "",
            companions   = 0,
            cost         = 0.0,
            status       = "Needs Info",
            date         = dateFmt.format(Date(startMs)),
            distanceKm   = distKm,
            durationSecs = durationS,
            routePoints  = routePoints.map { (lat, lng) -> "$lat,$lng" }
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

    fun discardTrip() {
        val ctx = appContext
        if (ctx != null && !_isSimulating.value) {
            if (isBound) {
                try { ctx.unbindService(connection) } catch (_: Exception) {}
                isBound = false
            }
            ctx.stopService(Intent(ctx, TrackingService::class.java))
        } else {
            legacyProvider.provider?.stopUpdates()
            legacyProvider.provider = null
        }
        resetState()
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private fun resetState() {
        _distanceKm.value       = 0.0
        _speedKmh.value         = 0.0
        _routePoints.value      = emptyList()
        _isLoading.value        = false
        _isTrackingActive.value = false
        _saveState.value        = SaveState.Idle
        legacyProvider.reset()
    }

    override fun onCleared() {
        appContext?.let { ctx ->
            if (isBound) {
                try { ctx.unbindService(connection) } catch (_: Exception) {}
            }
        }
        super.onCleared()
    }
}