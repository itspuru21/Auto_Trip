package com.example.autotrip.repository

import com.example.autotrip.model.EnhancedUserProfile
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Handles all Firestore operations related to the user profile.
 * Each user document lives at: users/{uid}
 * Fields trimmed to only what NATPAC research requires.
 */
class UserRepository {

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val usersCollection = db.collection("users")

    /**
     * Creates a new user profile document in Firestore after signup.
     * Called once immediately after FirebaseAuth.createUserWithEmailAndPassword succeeds.
     */
    suspend fun createUserProfile(
        uid: String,
        fullName: String,
        email: String
    ): Result<Unit> {
        return try {
            val profileData = mapOf(
                "fullName" to fullName,
                "email" to email,
                "phoneNumber" to "",
                "ageGroup" to "",
                "gender" to "",
                "occupation" to "",
                "householdSize" to 1,
                "numberOfVehicles" to 0,
                "ownsPersonalVehicle" to false,
                "vehicleType" to "",
                "hasDrivingLicense" to false,
                "primaryCommuteMode" to "",
                "workLocationType" to "",
                "residenceType" to "",
                "createdAt" to com.google.firebase.Timestamp.now()
            )
            usersCollection.document(uid).set(profileData).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches the full user profile from Firestore.
     */
    suspend fun getUserProfile(uid: String): Result<EnhancedUserProfile> {
        return try {
            val document = usersCollection.document(uid).get().await()
            if (!document.exists()) {
                return Result.failure(Exception("Profile not found"))
            }
            val profile = EnhancedUserProfile(
                fullName = document.getString("fullName") ?: "",
                email = document.getString("email") ?: "",
                phoneNumber = document.getString("phoneNumber") ?: "",
                ageGroup = document.getString("ageGroup") ?: "",
                gender = document.getString("gender") ?: "",
                occupation = document.getString("occupation") ?: "",
                householdSize = (document.getLong("householdSize") ?: 1L).toInt(),
                numberOfVehicles = (document.getLong("numberOfVehicles") ?: 0L).toInt(),
                ownsPersonalVehicle = document.getBoolean("ownsPersonalVehicle") ?: false,
                vehicleType = document.getString("vehicleType") ?: "",
                hasDrivingLicense = document.getBoolean("hasDrivingLicense") ?: false,
                primaryCommuteMode = document.getString("primaryCommuteMode") ?: "",
                workLocationType = document.getString("workLocationType") ?: "",
                residenceType = document.getString("residenceType") ?: "",
            )
            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Updates only the specified fields — does not overwrite the entire document.
     */
    suspend fun updateUserProfile(
        uid: String,
        updates: Map<String, Any>
    ): Result<Unit> {
        return try {
            usersCollection.document(uid).update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Permanently deletes the user's Firestore profile document.
     * Should be called alongside FirebaseAuth account deletion.
     */
    suspend fun deleteUserProfile(uid: String): Result<Unit> {
        return try {
            usersCollection.document(uid).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
