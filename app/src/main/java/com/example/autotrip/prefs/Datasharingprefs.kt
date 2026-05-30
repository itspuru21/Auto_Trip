package com.example.autotrip.prefs

import android.content.Context
import android.content.SharedPreferences

/**
 * Simple wrapper around SharedPreferences for the "Share Trip Data" setting.
 *
 * When [isTripSharingEnabled] is TRUE  → trips are saved to Firestore as normal.
 * When [isTripSharingEnabled] is FALSE → trip recording is blocked entirely;
 *   the user must turn the toggle back on before a new trip can be started.
 *
 * Personal profile data (name, email, demographics, etc.) is ALWAYS synced to
 * Firestore regardless of this flag — only trip data is affected.
 */
object DataSharingPrefs {

    private const val PREFS_NAME         = "autotrip_data_prefs"
    private const val KEY_TRIP_SHARING   = "trip_data_sharing_enabled"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Returns true (sharing ON) by default — opt-out model. */
    fun isTripSharingEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TRIP_SHARING, true)

    fun setTripSharingEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_TRIP_SHARING, enabled).apply()
    }
}