package com.example.autotrip.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.*
import androidx.core.app.NotificationCompat
import com.example.autotrip.MainActivity
import com.example.autotrip.model.Trip
import com.example.autotrip.repository.FirebaseAuthRepository
import com.example.autotrip.repository.TripRepository
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Foreground service that keeps GPS tracking alive when the app is backgrounded.
 *
 * Two stop paths:
 *  A) UI calls stopTracking() via binder → returns final state to ViewModel → ViewModel saves.
 *  B) User taps "End Trip" in notification → ACTION_STOP → service saves trip itself
 *     → writes tripId to SharedPreferences → MainActivity reads it on next open.
 */
class TrackingService : Service() {

    companion object {
        const val CHANNEL_ID        = "autotrip_tracking"
        const val NOTIF_ID          = 1001

        const val ACTION_START      = "com.example.autotrip.START_TRACKING"
        const val ACTION_STOP       = "com.example.autotrip.STOP_TRACKING"

        const val EXTRA_ORIGIN      = "origin"
        const val EXTRA_DESTINATION = "destination"

        const val PREFS_NAME        = "autotrip_prefs"
        const val KEY_PENDING_TRIP  = "pending_trip_id"

        private const val LOC_INTERVAL_MS  = 3_000L
        private const val LOC_FASTEST_MS   = 1_500L
        private const val MIN_DIST_METERS  = 5f
    }

    // ─────────────────────────────────────────────────────────────
    // State
    // ─────────────────────────────────────────────────────────────

    data class TrackingState(
        val isTracking  : Boolean                    = false,
        val distanceKm  : Double                     = 0.0,
        val speedKmh    : Double                     = 0.0,
        val elapsedSecs : Int                        = 0,
        val routePoints : List<Pair<Double, Double>> = emptyList(),
        val origin      : String                     = "",
        val destination : String                     = ""
    )

    @Volatile private var _state = TrackingState()
    val state get() = _state

    private val listeners = mutableListOf<(TrackingState) -> Unit>()
    fun addListener(l: (TrackingState) -> Unit)    { listeners.add(l) }
    fun removeListener(l: (TrackingState) -> Unit) { listeners.remove(l) }

    private fun emitState() {
        listeners.forEach { it(_state) }
        updateNotification()
    }

    // ─────────────────────────────────────────────────────────────
    // Binder
    // ─────────────────────────────────────────────────────────────

    inner class LocalBinder : Binder() {
        val service: TrackingService get() = this@TrackingService
    }

    private val binder = LocalBinder()
    override fun onBind(intent: Intent): IBinder = binder

    // ─────────────────────────────────────────────────────────────
    // Location
    // ─────────────────────────────────────────────────────────────

    private lateinit var fusedClient  : FusedLocationProviderClient
    private var locationCallback      : LocationCallback? = null
    private var lastLocation          : Location?          = null
    private var totalDistM            = 0.0
    private var startTimeMs           = 0L

    private val timerHandler  = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (_state.isTracking) {
                _state = _state.copy(
                    elapsedSecs = ((System.currentTimeMillis() - startTimeMs) / 1000).toInt()
                )
                emitState()
                timerHandler.postDelayed(this, 1_000L)
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ─────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val origin = intent.getStringExtra(EXTRA_ORIGIN) ?: "Start"
                val dest   = intent.getStringExtra(EXTRA_DESTINATION) ?: "Destination"
                startTracking(origin, dest)
            }
            ACTION_STOP -> saveAndStop()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        stopHardware()
        super.onDestroy()
    }

    // ─────────────────────────────────────────────────────────────
    // Tracking control — path A (bound UI)
    // ─────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun startTracking(origin: String, destination: String) {
        if (_state.isTracking) return

        totalDistM   = 0.0
        lastLocation = null
        startTimeMs  = System.currentTimeMillis()
        _state       = TrackingState(isTracking = true, origin = origin, destination = destination)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIF_ID, buildNotification())
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOC_INTERVAL_MS)
            .setMinUpdateIntervalMillis(LOC_FASTEST_MS)
            .setMinUpdateDistanceMeters(MIN_DIST_METERS)
            .setWaitForAccurateLocation(false)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { onLocationReceived(it) }
            }
        }

        fusedClient.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
        timerHandler.post(timerRunnable)
        emitState()
    }

    /** Called by ViewModel when app is in foreground. Returns snapshot for ViewModel to save. */
    fun stopTracking(): TrackingState {
        val snapshot = _state
        stopHardware()
        _state = snapshot.copy(isTracking = false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        return snapshot
    }

    // ─────────────────────────────────────────────────────────────
    // Tracking control — path B (notification "End Trip" button)
    // ─────────────────────────────────────────────────────────────

    private fun saveAndStop() {
        val snapshot = _state
        stopHardware()
        _state = snapshot.copy(isTracking = false)
        stopForeground(STOP_FOREGROUND_REMOVE)

        if (snapshot.distanceKm < 0.05 && snapshot.elapsedSecs < 10) {
            stopSelf(); return
        }

        serviceScope.launch {
            val uid = FirebaseAuthRepository().currentUser?.uid
            if (uid == null) { stopSelf(); return@launch }

            val timeFmt   = SimpleDateFormat("h:mm a",    Locale.getDefault())
            val dateFmt   = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val endTimeMs = startTimeMs + (snapshot.elapsedSecs * 1000L)

            val trip = Trip(
                origin       = snapshot.origin,
                destination  = snapshot.destination,
                startTime    = timeFmt.format(Date(startTimeMs)),
                endTime      = timeFmt.format(Date(endTimeMs)),
                travelMode   = "",
                purpose      = "",
                companions   = 0,
                cost         = 0.0,
                status       = "Needs Info",
                date         = dateFmt.format(Date(startTimeMs)),
                distanceKm   = snapshot.distanceKm,
                durationSecs = snapshot.elapsedSecs,
                routePoints  = snapshot.routePoints.map { (lat, lng) -> "$lat,$lng" }
            )

            TripRepository().saveTrip(uid, trip)
            stopSelf()
        }
    }


    override fun onTaskRemoved(rootIntent: Intent?) {
        // User swiped the app away without tapping End Trip — discard silently
        stopHardware()
        _state = _state.copy(isTracking = false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        serviceScope.cancel()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }
    // ─────────────────────────────────────────────────────────────
    // Location processing
    // ─────────────────────────────────────────────────────────────

    private fun onLocationReceived(loc: Location) {
        lastLocation?.let { prev ->
            val delta = prev.distanceTo(loc)
            if (delta > MIN_DIST_METERS) totalDistM += delta
        }
        lastLocation = loc

        val speed  = if (loc.hasSpeed() && loc.speed > 0f) loc.speed * 3.6 else _state.speedKmh
        val newPts = _state.routePoints + Pair(loc.latitude, loc.longitude)

        _state = _state.copy(
            distanceKm  = totalDistM / 1000.0,
            speedKmh    = speed,
            routePoints = newPts
        )
        emitState()
    }

    private fun stopHardware() {
        timerHandler.removeCallbacks(timerRunnable)
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        locationCallback = null
        lastLocation     = null
    }

    // ─────────────────────────────────────────────────────────────
    // Notification
    // ─────────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Trip Tracking", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows while a trip is being recorded"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, TrackingService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val s    = _state
        val km   = "%.2f km".format(s.distanceKm)
        val mins = s.elapsedSecs / 60
        val secs = s.elapsedSecs % 60
        val time = "%02d:%02d".format(mins, secs)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("🟢 ${s.origin} → ${s.destination}")
            .setContentText("$km  ·  $time")
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "End Trip", stopIntent)
            .build()
    }

    private fun updateNotification() {
        if (!_state.isTracking) return
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }
}