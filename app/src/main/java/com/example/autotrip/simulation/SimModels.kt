package com.example.autotrip.simulation

/**
 * Transport modes available in the simulator.
 * avgSpeedKmh is the realistic average for Indian urban conditions.
 */
enum class SimMode(
    val label       : String,
    val avgSpeedKmh : Double,
    val emoji       : String
) {
    WALK        ("Walking",       5.0,  "🚶"),
    BICYCLE     ("Bicycle",      15.0,  "🚲"),
    AUTO        ("Auto-Rickshaw",20.0,  "🛺"),
    BUS         ("Bus",          25.0,  "🚌"),
    CAR         ("Car",          40.0,  "🚗"),
    METRO       ("Metro",        60.0,  "🚇")
}

/**
 * A named lat/lng location used as origin or destination.
 * Built-in presets cover common Aurangabad (NATPAC) locations.
 * Users can also enter custom coordinates.
 */
data class SimPreset(
    val name : String,
    val lat  : Double,
    val lng  : Double
) {
    override fun toString() = name
}

/** Built-in presets — Aurangabad city routes for realistic NATPAC testing. */
object SimPresets {
    val ALL = listOf(
        SimPreset("Aurangabad Station",    19.8762, 75.3433),
        SimPreset("Aurangabad Airport",    19.8627, 75.3981),
        SimPreset("MIT College",           19.8745, 75.3788),
        SimPreset("Prozone Mall",          19.8901, 75.3392),
        SimPreset("CIDCO Bus Stand",       19.8724, 75.3566),
        SimPreset("Gulmandi",              19.8954, 75.3234),
        SimPreset("Ajanta Caves Turnoff",  20.5519, 75.7033),
        SimPreset("Ellora Caves",          20.0258, 75.1781),
        SimPreset("Custom",                0.0,     0.0     )  // sentinel for custom lat/lng
    )
    val DEFAULT_ORIGIN      get() = ALL[0]  // Station
    val DEFAULT_DESTINATION get() = ALL[2]  // MIT College
}
