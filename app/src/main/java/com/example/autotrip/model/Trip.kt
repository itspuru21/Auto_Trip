package com.example.autotrip.model

/**
 * Represents a single recorded or manually logged trip.
 * Clean and simple data model.
 */
data class Trip(
    val id: String = "",
    val origin: String = "",
    val destination: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val travelMode: String = "",
    val purpose: String = "",
    val companions: Int = 0,
    val cost: Double = 0.0,
    val status: String = "",   // "Auto-logged" / "Needs Info"
    val date: String = ""      // Optional: yyyy-MM-dd
)
