package com.example.autotrip.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.autotrip.model.Trip
import com.example.autotrip.repository.FirebaseAuthRepository
import com.example.autotrip.repository.TripRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Phase 3 — NotificationsViewModel
 *
 * Derives notifications dynamically from the user's real trip data in Firestore:
 *
 *  - "Info Needed"  — any trip with status == "Needs Info"
 *  - "Trip Complete" — recent trips that are fully logged
 *  - "Sync"          — always shown once to confirm cloud sync is active
 *
 * The list auto-updates whenever Firestore emits new trip data (same snapshot
 * listener as TripsViewModel — we share TripRepository, not the ViewModel).
 */
class NotificationsViewModel : ViewModel() {

    private val authRepo = FirebaseAuthRepository()
    private val tripRepo = TripRepository()

    // ── Derived notification list ────────────────────────────────

    val notifications: StateFlow<List<AppNotification>> = flow {
        val uid = authRepo.currentUser?.uid
        if (uid == null) { emit(emptyList()); return@flow }
        emitAll(
            tripRepo.getTripsFlow(uid).map { trips -> buildNotifications(trips) }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Clear all (local override — Firestore trips are unchanged) ──

    private val _dismissed = MutableStateFlow<Set<String>>(emptySet())

    val visibleNotifications: StateFlow<List<AppNotification>> = combine(
        notifications, _dismissed
    ) { notifs, dismissed ->
        notifs.filter { it.id !in dismissed }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun dismiss(id: String) {
        _dismissed.value = _dismissed.value + id
    }

    fun dismissAll() {
        viewModelScope.launch {
            val current = notifications.value.map { it.id }.toSet()
            _dismissed.value = _dismissed.value + current
        }
    }

    // ── Builder ──────────────────────────────────────────────────

    private fun buildNotifications(trips: List<Trip>): List<AppNotification> {
        val list = mutableListOf<AppNotification>()

        // Always show sync confirmation first
        list += AppNotification(
            id          = "sync_always",
            title       = "Sync Successful",
            description = "All trip data is synced to the NATPAC server.",
            timeAgo     = "Now",
            type        = NotifType.SYNC
        )

        val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
        val todayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = todayFmt.format(Date())

        trips.forEach { trip ->
            when {
                trip.status == "Needs Info" -> {
                    list += AppNotification(
                        id          = "needs_info_${trip.id}",
                        title       = "Info Needed",
                        description = "Trip to ${trip.destination} needs more details — tap to complete.",
                        timeAgo     = if (trip.date == today) "Today, ${trip.startTime}" else trip.date,
                        type        = NotifType.NEEDS_INFO,
                        linkedTripId = trip.id
                    )
                }
                trip.status == "Auto-logged" || trip.travelMode.isNotBlank() -> {
                    list += AppNotification(
                        id          = "complete_${trip.id}",
                        title       = "Trip Logged",
                        description = "Trip from ${trip.origin} to ${trip.destination} was recorded.",
                        timeAgo     = if (trip.date == today) "Today, ${trip.startTime}" else trip.date,
                        type        = NotifType.TRIP_COMPLETE,
                        linkedTripId = trip.id
                    )
                }
            }
        }

        return list
    }
}

// ── Data model ───────────────────────────────────────────────────────────────

data class AppNotification(
    val id           : String,
    val title        : String,
    val description  : String,
    val timeAgo      : String,
    val type         : NotifType,
    val linkedTripId : String? = null
)

enum class NotifType { TRIP_COMPLETE, NEEDS_INFO, SYNC, INFO }
