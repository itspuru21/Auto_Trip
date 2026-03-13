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
 * Document path: users/{uid}/trips/{tripId}
 */
class TripRepository {

    private val db = FirebaseFirestore.getInstance()

    private fun tripsRef(uid: String) =
        db.collection("users").document(uid).collection("trips")

    // ─────────────────────────────────────────────────────────────
    // READ
    // ─────────────────────────────────────────────────────────────

    fun getTripsFlow(uid: String): Flow<List<Trip>> = callbackFlow {
        val listener = tripsRef(uid)
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val trips = snapshot?.documents?.mapNotNull { doc ->
                    runCatching { doc.toTrip() }.getOrNull()
                } ?: emptyList()
                trySend(trips)
            }
        awaitClose { listener.remove() }
    }

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

    @Suppress("UNCHECKED_CAST")
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
            date        = getString("date")        ?: "",
            distanceKm  = getDouble("distanceKm")  ?: 0.0,
            routePoints = (get("routePoints") as? List<String>) ?: emptyList()
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
        "date"        to date,
        "distanceKm"  to distanceKm,
        "routePoints" to routePoints
    )
}