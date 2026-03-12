package com.example.autotrip.state

import androidx.compose.runtime.mutableStateOf

/**
 * Global UI state for connectivity and sync animations.
 * Used by AutoTripTopBar.
 */
object SyncState {
    val isSyncing = mutableStateOf(false)
    val isOffline = mutableStateOf(false)
}
