package com.example.autotrip.location

import android.location.Location

/**
 * Simple mutable bag that holds simulation-specific state so the
 * refactored ActiveTrackingViewModel can keep simulation logic
 * self-contained without mixing it into the service-binding path.
 */
class LegacyProviderHolder {
    var provider     : SimulatedLocationProvider? = null
    var lastLocation : Location?                  = null
    var totalDistM   : Double                     = 0.0
    var startTimeMs  : Long                       = 0L
    var simSpeedKmh  : Double                     = 0.0
    var isLoading    : Boolean                    = false

    fun reset() {
        provider     = null
        lastLocation = null
        totalDistM   = 0.0
        startTimeMs  = 0L
        simSpeedKmh  = 0.0
        isLoading    = false
    }
}