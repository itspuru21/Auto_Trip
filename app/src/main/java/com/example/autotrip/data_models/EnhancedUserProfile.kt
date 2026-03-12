package com.example.autotrip.model

/**
 * Extended profile used for surveys, research & planning.
 * Completely optional but improves analytics accuracy.
 */
data class EnhancedUserProfile(

    // Basic Information
    val fullName: String = "",
    val email: String = "",
    val phoneNumber: String = "",

    // Demographics
    val ageGroup: String = "",
    val gender: String = "",
    val occupation: String = "",
    val educationLevel: String = "",
    val householdIncome: String = "",

    // Household Info
    val householdSize: Int = 1,
    val numberOfChildren: Int = 0,
    val numberOfWorkingAdults: Int = 1,
    val residenceType: String = "",
    val residenceOwnership: String = "",
    val yearsAtCurrentResidence: Int = 0,

    // Vehicle Ownership
    val ownsPersonalVehicle: Boolean = false,
    val vehicleType: String = "",
    val vehicleYear: String = "",
    val numberOfVehicles: Int = 0,
    val hasDrivingLicense: Boolean = false,
    val licenseYears: Int = 0,

    // Transportation Preferences
    val primaryCommuteMode: String = "",
    val workLocationType: String = "",
    val studyLocationType: String = "",
    val workFromHomeFrequency: String = "",

    // Disability / Special Needs
    val hasDisability: Boolean = false,
    val disabilityType: String = "",
    val requiresSpecialTransport: Boolean = false
)
