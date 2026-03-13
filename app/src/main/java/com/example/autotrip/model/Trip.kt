package com.example.autotrip.model

/**
 * Represents a single recorded or manually logged trip.
 *
 * [routePoints] stores the GPS breadcrumb trail as a list of
 * "lat,lng" strings (e.g. "19.8762,75.3433").  These are saved
 * to Firestore so the TripDetailsScreen can replay the polyline
 * on an OSM map even after the trip ends.
 */
data class Trip(
    val id          : String = "",
    val origin      : String = "",
    val destination : String = "",
    val startTime   : String = "",
    val endTime     : String = "",
    val travelMode  : String = "",
    val purpose     : String = "",
    val companions  : Int    = 0,
    val cost        : Double = 0.0,
    val status      : String = "",   // "Auto-logged" / "Needs Info"
    val date        : String = "",   // yyyy-MM-dd
    val distanceKm  : Double = 0.0,
    // Breadcrumb trail — each entry is "lat,lng"
    val routePoints : List<String> = emptyList()
)