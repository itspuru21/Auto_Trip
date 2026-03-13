package com.example.autotrip.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.autotrip.model.Trip
import com.example.autotrip.repository.FirebaseAuthRepository
import com.example.autotrip.repository.TripRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class TripsViewModel : ViewModel() {

    private val authRepo = FirebaseAuthRepository()
    private val tripRepo = TripRepository()

    // ── All trips, real-time from Firestore ─────────────────────
    val trips: StateFlow<List<Trip>> = flow {
        val uid = authRepo.currentUser?.uid
        if (uid == null) { emit(emptyList()); return@flow }
        emitAll(tripRepo.getTripsFlow(uid))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Save / update state ──────────────────────────────────────
    private val _saveState = MutableStateFlow<TripSaveState>(TripSaveState.Idle)
    val saveState: StateFlow<TripSaveState> = _saveState

    /**
     * Directly fetch a single trip from Firestore by ID.
     * Used by TripDetailsScreen when navigating immediately after saving (the
     * real-time flow may not have the new trip yet, causing the screen to
     * show a blank skeleton or freeze while scanning an empty list).
     * Call this on Dispatchers.IO.
     */
    suspend fun fetchTripById(tripId: String): Trip? {
        val uid = authRepo.currentUser?.uid ?: return null
        return tripRepo.getTripById(uid, tripId).getOrNull()
    }

    fun updateTrip(tripId: String, updates: Map<String, Any>) {
        val uid = authRepo.currentUser?.uid ?: run {
            _saveState.value = TripSaveState.Error("Not logged in"); return
        }
        viewModelScope.launch {
            _saveState.value = TripSaveState.Saving
            val result = tripRepo.updateTrip(uid, tripId, updates)
            _saveState.value = if (result.isSuccess) TripSaveState.Saved
            else TripSaveState.Error(result.exceptionOrNull()?.message ?: "Save failed")
        }
    }

    fun deleteTrip(tripId: String) {
        val uid = authRepo.currentUser?.uid ?: return
        viewModelScope.launch {
            tripRepo.deleteTrip(uid, tripId)
            // Firestore snapshot listener auto-updates the StateFlow
        }
    }

    fun resetSaveState() { _saveState.value = TripSaveState.Idle }
}

// Kept as top-level so TripDetailsScreen can import it directly
sealed class TripSaveState {
    object Idle    : TripSaveState()
    object Saving  : TripSaveState()
    object Saved   : TripSaveState()
    data class Error(val message: String) : TripSaveState()
}