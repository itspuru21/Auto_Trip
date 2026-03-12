package com.example.autotrip.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.*

/**
 * Production implementation — delegates to FusedLocationProviderClient.
 * Requires ACCESS_FINE_LOCATION permission to be granted before calling startUpdates().
 */
class RealLocationProvider(private val context: Context) : LocationProvider {

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)
    private var callback: LocationCallback? = null

    @SuppressLint("MissingPermission")
    override fun startUpdates(onLocation: (Location) -> Unit) {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 3_000L
        ).apply {
            setMinUpdateDistanceMeters(5f)
            setWaitForAccurateLocation(false)
        }.build()

        callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { onLocation(it) }
            }
        }

        fusedClient.requestLocationUpdates(request, callback!!, Looper.getMainLooper())
    }

    override fun stopUpdates() {
        callback?.let { fusedClient.removeLocationUpdates(it) }
        callback = null
    }
}
