package com.example.autotrip.simulation

// AFTER:
enum class SimMode(
    val label       : String,
    val avgSpeedKmh : Double,
    val emoji       : String
) {
    CAR    ("Car",           32.0,  "🚗"),   // city traffic avg ~25-30 km/h
    BUS    ("Bus",           26.0,  "🚌"),   // stops + traffic ~15-20 km/h
    AUTO   ("Auto-Rickshaw", 30.0,  "🛺"),   // weaves through traffic ~20-25 km/h
    BIKE   ("Bike",          32.0,  "🏍️"),  // motorbike, faster lane filtering
    OTHER  ("Other",         25.0,  "🚘")   // generic vehicle
}

data class SimPreset(
    val name : String,
    val lat  : Double,
    val lng  : Double
) { override fun toString() = name }

/**
 * All presets are within Aurangabad city — distances from Station:
 *  CIDCO Bus Stand  ~1.5 km  |  MIT College   ~2.0 km
 *  Prozone Mall     ~1.8 km  |  Gulmandi      ~2.5 km
 *  Kranti Chowk     ~3.0 km  |  Mondha Market ~2.8 km
 *  Airport          ~4.5 km
 */
object SimPresets {
    val ALL = listOf(
        SimPreset("Aurangabad Station",  19.8762, 75.3433),
        SimPreset("CIDCO Bus Stand",     19.8724, 75.3566),
        SimPreset("MIT College",         19.8745, 75.3788),
        SimPreset("Prozone Mall",        19.8901, 75.3392),
        SimPreset("Gulmandi",            19.8954, 75.3234),
        SimPreset("Kranti Chowk",        19.8832, 75.3612),
        SimPreset("Mondha Market",       19.8870, 75.3710),
        SimPreset("Aurangabad Airport",  19.8627, 75.3981),
        SimPreset("Custom",              0.0,     0.0     )
    )
    val DEFAULT_ORIGIN      get() = ALL[0]
    val DEFAULT_DESTINATION get() = ALL[2]
}