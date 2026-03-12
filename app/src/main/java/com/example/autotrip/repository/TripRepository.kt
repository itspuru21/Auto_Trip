package com.example.autotrip.repository

import com.example.autotrip.model.Trip
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Handles all Firestore operations for Trip documents.
 *
 * Document path: users/{uid}/trips/{tripId}
 *
 * All trips belong to a user — this ensures data isolation and
 * makes per-user queries fast and cheap.
 */
class TripRepository {

    private val db = FirebaseFirestore.getInstance()

    /** Returns the trips sub-collection for a given user. */
    private fun tripsRef(uid: String) =
        db.collection("users").document(uid).collection("trips")

    // ─────────────────────────────────────────────────────────────
    // READ
    // ─────────────────────────────────────────────────────────────

    /**
     * Real-time flow of all trips for the given user, ordered by date desc.
     * The UI collects this; any Firestore write (including from simulation)
     * immediately reflects in the UI without a manual refresh.
     */
    fun getTripsFlow(uid: String): Flow<List<Trip>> = callbackFlow {
        val listener = tripsRef(uid)
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val trips = snapshot?.documents?.mapNotNull { doc ->
                    runCatching { doc.toTrip() }.getOrNull()
                } ?: emptyList()
                trySend(trips)
            }
        awaitClose { listener.remove() }
    }

    /**
     * One-shot fetch — useful for detail screens or refresh-on-demand.
     */
    suspend fun getTripById(uid: String, tripId: String): Result<Trip> {
        return try {
            val doc = tripsRef(uid).document(tripId).get().await()
            if (!doc.exists()) return Result.failure(Exception("Trip not found"))
            Result.success(doc.toTrip())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // WRITE
    // ─────────────────────────────────────────────────────────────

    /**
     * Creates a new trip document.
     * If [trip.id] is blank a Firestore-generated ID is used.
     */
    suspend fun saveTrip(uid: String, trip: Trip): Result<String> {
        return try {
            val ref = if (trip.id.isBlank()) tripsRef(uid).document()
                      else tripsRef(uid).document(trip.id)
            ref.set(trip.toMap()).await()
            Result.success(ref.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Partial update — only the supplied fields are written.
     * Used by TripDetailsScreen after the user fills in mode / purpose / cost.
     */
    suspend fun updateTrip(uid: String, tripId: String, updates: Map<String, Any>): Result<Unit> {
        return try {
            tripsRef(uid).document(tripId).update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteTrip(uid: String, tripId: String): Result<Unit> {
        return try {
            tripsRef(uid).document(tripId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // MAPPERS
    // ─────────────────────────────────────────────────────────────

    private fun com.google.firebase.firestore.DocumentSnapshot.toTrip(): Trip =
        Trip(
            id          = id,
            origin      = getString("origin")      ?: "",
            destination = getString("destination") ?: "",
            startTime   = getString("startTime")   ?: "",
            endTime     = getString("endTime")     ?: "",
            travelMode  = getString("travelMode")  ?: "",
            purpose     = getString("purpose")     ?: "",
            companions  = (getLong("companions")   ?: 0L).toInt(),
            cost        = getDouble("cost")        ?: 0.0,
            status      = getString("status")      ?: "",
            date        = getString("date")        ?: ""
        )

    private fun Trip.toMap(): Map<String, Any> = mapOf(
        "origin"      to origin,
        "destination" to destination,
        "startTime"   to startTime,
        "endTime"     to endTime,
        "travelMode"  to travelMode,
        "purpose"     to purpose,
        "companions"  to companions,
        "cost"        to cost,
        "status"      to status,
        "date"        to date
    )
}
