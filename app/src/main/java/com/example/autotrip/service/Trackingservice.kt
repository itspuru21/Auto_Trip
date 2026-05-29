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
 * Stop paths:
 *  A) UI calls stopTracking() via binder → returns final state to ViewModel → ViewModel saves.
 *     The ViewModel is responsible for calling unbindService AFTER it has the snapshot.
 *
 *  B) User taps "End Trip" in notification → ACTION_STOP intent → service saves trip itself,
 *     writes tripId to SharedPreferences → sets a "stopped by notification" flag so the
 *     bound ViewModel can react when it next polls / re-binds.
 *
 *  C) onTaskRemoved (user swiped app away) → saves the trip so data isn't lost silently.
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
        val isTracking      : Boolean                    = false,
        val distanceKm      : Double                     = 0.0,
        val speedKmh        : Double                     = 0.0,
        val elapsedSecs     : Int                        = 0,
        val routePoints     : List<Pair<Double, Double>> = emptyList(),
        val origin          : String                     = "",
        val destination     : String                     = "",
        // True when the notification "End Trip" button fired — ViewModel should navigate
        val stoppedByNotification : Boolean              = false
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
    // Path A — ViewModel-driven stop (app in foreground, End Trip button)
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

    /**
     * Called by ViewModel when the user taps "End Trip" while the app is visible.
     * Returns a snapshot for the ViewModel to save — does NOT stop the service itself,
     * since the ViewModel will unbind and then call stopService() after saving.
     * This avoids the race condition where the service is destroyed before unbindService().
     */
    fun stopTracking(): TrackingState {
        val snapshot = _state.copy(isTracking = false)
        stopHardware()
        _state = snapshot
        // Don't call stopForeground or stopSelf here — let ViewModel handle teardown
        // after it finishes saving, by calling stopService() explicitly.
        stopForeground(STOP_FOREGROUND_REMOVE)
        return snapshot
    }

    /**
     * Called by the ViewModel after it has finished saving the trip.
     * Safe to call even if already stopped.
     */
    fun shutdownSelf() {
        stopSelf()
    }

    // ─────────────────────────────────────────────────────────────
    // Path B — Notification "End Trip" button (app in background)
    // ─────────────────────────────────────────────────────────────

    private fun saveAndStop() {
        val snapshot = _state
        stopHardware()
        stopForeground(STOP_FOREGROUND_REMOVE)

        // Emit a special state so any currently-bound ViewModel knows to navigate away
        _state = snapshot.copy(isTracking = false, stoppedByNotification = true)
        emitState()

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

            val result = TripRepository().saveTrip(uid, trip)
            if (result.isSuccess) {
                // Write tripId to SharedPreferences so MainActivity can pick it up on next resume
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putString(KEY_PENDING_TRIP, result.getOrNull())
                    .apply()
            }
            stopSelf()
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Path C — User swiped app away (save so data isn't lost)
    // ─────────────────────────────────────────────────────────────

    override fun onTaskRemoved(rootIntent: Intent?) {
        val snapshot = _state
        stopHardware()
        _state = snapshot.copy(isTracking = false)
        stopForeground(STOP_FOREGROUND_REMOVE)

        // Only save if a meaningful trip was recorded (>50m and >10s)
        if (snapshot.distanceKm >= 0.05 && snapshot.elapsedSecs >= 10 && snapshot.isTracking) {
            serviceScope.launch {
                val uid = FirebaseAuthRepository().currentUser?.uid
                if (uid != null) {
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
                }
                stopSelf()
            }
        } else {
            serviceScope.cancel()
            stopSelf()
        }

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

        // Tapping the notification body returns to the app
        val openIntent = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
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
            .setContentIntent(openIntent)
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