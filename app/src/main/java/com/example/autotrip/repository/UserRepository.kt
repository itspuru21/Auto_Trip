package com.example.autotrip.repository

import com.example.autotrip.model.EnhancedUserProfile
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Handles all Firestore operations related to the user profile.
 * Each user document lives at: users/{uid}
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
                "educationLevel" to "",
                "householdIncome" to "",
                "householdSize" to 1,
                "numberOfChildren" to 0,
                "numberOfWorkingAdults" to 1,
                "residenceType" to "",
                "residenceOwnership" to "",
                "yearsAtCurrentResidence" to 0,
                "ownsPersonalVehicle" to false,
                "vehicleType" to "",
                "vehicleYear" to "",
                "numberOfVehicles" to 0,
                "hasDrivingLicense" to false,
                "licenseYears" to 0,
                "primaryCommuteMode" to "",
                "workLocationType" to "",
                "studyLocationType" to "",
                "workFromHomeFrequency" to "",
                "hasDisability" to false,
                "disabilityType" to "",
                "requiresSpecialTransport" to false,
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
     * Maps Firestore document fields to EnhancedUserProfile model.
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
                educationLevel = document.getString("educationLevel") ?: "",
                householdIncome = document.getString("householdIncome") ?: "",
                householdSize = (document.getLong("householdSize") ?: 1L).toInt(),
                numberOfChildren = (document.getLong("numberOfChildren") ?: 0L).toInt(),
                numberOfWorkingAdults = (document.getLong("numberOfWorkingAdults") ?: 1L).toInt(),
                residenceType = document.getString("residenceType") ?: "",
                residenceOwnership = document.getString("residenceOwnership") ?: "",
                yearsAtCurrentResidence = (document.getLong("yearsAtCurrentResidence") ?: 0L).toInt(),
                ownsPersonalVehicle = document.getBoolean("ownsPersonalVehicle") ?: false,
                vehicleType = document.getString("vehicleType") ?: "",
                vehicleYear = document.getString("vehicleYear") ?: "",
                numberOfVehicles = (document.getLong("numberOfVehicles") ?: 0L).toInt(),
                hasDrivingLicense = document.getBoolean("hasDrivingLicense") ?: false,
                licenseYears = (document.getLong("licenseYears") ?: 0L).toInt(),
                primaryCommuteMode = document.getString("primaryCommuteMode") ?: "",
                workLocationType = document.getString("workLocationType") ?: "",
                studyLocationType = document.getString("studyLocationType") ?: "",
                workFromHomeFrequency = document.getString("workFromHomeFrequency") ?: "",
                hasDisability = document.getBoolean("hasDisability") ?: false,
                disabilityType = document.getString("disabilityType") ?: "",
                requiresSpecialTransport = document.getBoolean("requiresSpecialTransport") ?: false
            )
            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Updates only the fields the user can edit from the profile screen.
     * Uses Firestore update() so only specified fields change — rest stay intact.
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
}
