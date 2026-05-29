package com.example.autotrip.viewmodel

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.autotrip.model.Trip
import com.example.autotrip.repository.FirebaseAuthRepository
import com.example.autotrip.repository.TripRepository
import com.example.autotrip.service.TrackingService
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

    private val _speedKmh = MutableStateFlow(0.0)
    val speedKmh: StateFlow<Double> = _speedKmh

    private val _routePoints = MutableStateFlow<List<Pair<Double, Double>>>(emptyList())
    val routePoints: StateFlow<List<Pair<Double, Double>>> = _routePoints

    private val _isSimulating = MutableStateFlow(false)
    val isSimulating: StateFlow<Boolean> = _isSimulating

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

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

        // Loading is true only while tracking is active AND no GPS fix yet.
        // Once we have at least one point the loading spinner goes away.
        _isLoading.value = state.isTracking && state.routePoints.isEmpty()

        // Handle the case where the user tapped "End Trip" in the notification
        // while the app was in the foreground (or came back to foreground after).
        // The service marks itself with stoppedByNotification; we must navigate.
        if (state.stoppedByNotification && _saveState.value is SaveState.Idle) {
            handleNotificationStop(state)
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val service = (binder as TrackingService.LocalBinder).service
            boundService = service
            service.addListener(stateListener)
            // Sync immediately — covers the case where service was already running
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
            try { context.unbindService(connection) } catch (_: IllegalArgumentException) {}
            isBound      = false
            boundService = null
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Simulation — unchanged
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
    // End trip (dispatches to real or sim path)
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

        // 1. Get final snapshot from service BEFORE touching the binding.
        //    stopTracking() halts hardware but does NOT call stopSelf().
        val finalState = boundService?.stopTracking()

        // 2. Unbind. This is safe now because we already have the snapshot.
        unbindFromService(ctx)

        val routePts   = finalState?.routePoints ?: _routePoints.value
        val distKm     = finalState?.distanceKm  ?: _distanceKm.value
        val durationS  = finalState?.elapsedSecs ?: elapsedUiSecs

        saveTrip(origin, destination, distKm, durationS, routePts, isSimMode = false) {
            // 3. Only after saving, stop the service.
            ctx.stopService(Intent(ctx, TrackingService::class.java))
        }
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
     * Handle the case where "End Trip" was tapped in the notification while the
     * service was bound to this ViewModel. The service has already saved the trip
     * itself and written the tripId to SharedPreferences — we just need to clean up
     * the binding and navigate.
     */
    private fun handleNotificationStop(state: TrackingService.TrackingState) {
        val ctx = appContext ?: return
        unbindFromService(ctx)
        ctx.stopService(Intent(ctx, TrackingService::class.java))

        // Read the tripId the service wrote to prefs
        val prefs  = ctx.getSharedPreferences(TrackingService.PREFS_NAME, Context.MODE_PRIVATE)
        val tripId = prefs.getString(TrackingService.KEY_PENDING_TRIP, null)
        prefs.edit().remove(TrackingService.KEY_PENDING_TRIP).apply()

        if (!tripId.isNullOrBlank()) {
            _saveState.value = SaveState.Saved(tripId)
        }
        // If no tripId (e.g. save failed silently), leave saveState as Idle so
        // the screen stays put rather than navigating to a blank trip.
    }

    private fun saveTrip(
        origin      : String,
        destination : String,
        distKm      : Double,
        durationS   : Int,
        routePoints : List<Pair<Double, Double>>,
        isSimMode   : Boolean,
        onSaved     : (() -> Unit)? = null
    ) {
        val uid = authRepo.currentUser?.uid
        if (uid == null) { _saveState.value = SaveState.Error("Not logged in"); return }

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
            _saveState.value = if (result.isSuccess) {
                onSaved?.invoke()
                SaveState.Saved(result.getOrNull()!!)
            } else {
                SaveState.Error(result.exceptionOrNull()?.message ?: "Save failed")
            }
        }
    }

    fun discardTrip() {
        val ctx = appContext
        if (ctx != null && !_isSimulating.value) {
            boundService?.stopTracking()   // halt hardware first
            unbindFromService(ctx)
            ctx.stopService(Intent(ctx, TrackingService::class.java))
        } else {
            legacyProvider.provider?.stopUpdates()
            legacyProvider.provider = null
        }
        resetState()
    }

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
        // ViewModel destroyed — unbind but leave service alive if still tracking
        appContext?.let { ctx ->
            if (isBound) {
                boundService?.removeListener(stateListener)
                try { ctx.unbindService(connection) } catch (_: IllegalArgumentException) {}
                isBound = false
            }
        }
        super.onCleared()
    }
}