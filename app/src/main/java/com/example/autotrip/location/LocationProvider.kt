package com.example.autotrip.location

import android.location.Location

/**
 * Abstraction over location sources.
 * ActiveTrackingViewModel depends on this interface — it never knows
 * whether it's talking to a real GPS or the simulator.
 */
interface LocationProvider {
    fun startUpdates(onLocation: (Location) -> Unit)
    fun stopUpdates()
}
