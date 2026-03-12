package com.example.autotrip.model

/**
 * Basic user model for authentication and user identity.
 * Additional profile fields are stored in EnhancedUserProfile.
 */
data class User(
    val name: String = "",
    val email: String = "",
    val ageGroup: String = "",
    val occupation: String = "",
    val ownsVehicle: Boolean = false
)
