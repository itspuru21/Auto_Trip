package com.example.autotrip.model

/**
 * User profile fields needed for NATPAC travel research.
 * Trimmed to only what's necessary for trip analysis and planning.
 */
data class EnhancedUserProfile(

    // Identity
    val fullName: String = "",
    val email: String = "",
    val phoneNumber: String = "",

    // Demographics (needed for trip pattern analysis)
    val ageGroup: String = "",          // e.g. "18-25", "26-35", etc.
    val gender: String = "",
    val occupation: String = "",        // affects trip purpose distribution

    // Household (affects trip generation rates)
    val householdSize: Int = 1,
    val numberOfVehicles: Int = 0,

    // Vehicle & License (determines mode availability)
    val ownsPersonalVehicle: Boolean = false,
    val vehicleType: String = "",       // "Two-Wheeler", "Car", "None", etc.
    val hasDrivingLicense: Boolean = false,

    // Commute baseline (core research data)
    val primaryCommuteMode: String = "",   // dominant mode used
    val workLocationType: String = "",     // "Office", "Remote", "Hybrid"
    val residenceType: String = "",        // "Urban", "Semi-Urban", "Rural"
)
