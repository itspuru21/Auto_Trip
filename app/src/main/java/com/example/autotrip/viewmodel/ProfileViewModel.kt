package com.example.autotrip.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.autotrip.model.EnhancedUserProfile
import com.example.autotrip.repository.FirebaseAuthRepository
import com.example.autotrip.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Manages profile data for EnhancedProfileScreen.
 * Loads user data from Firestore on init and handles save updates.
 */
class ProfileViewModel : ViewModel() {

    private val authRepo = FirebaseAuthRepository()
    private val userRepo = UserRepository()

    private val _profileState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val profileState: StateFlow<ProfileUiState> = _profileState

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState

    init {
        loadProfile()
    }

    /**
     * Loads the user profile from Firestore.
     * Called automatically when ViewModel is created.
     */
    fun loadProfile() {
        val uid = authRepo.currentUser?.uid
        if (uid == null) {
            _profileState.value = ProfileUiState.Error("Not logged in")
            return
        }

        viewModelScope.launch {
            _profileState.value = ProfileUiState.Loading
            val result = userRepo.getUserProfile(uid)
            _profileState.value = if (result.isSuccess) {
                ProfileUiState.Success(result.getOrNull()!!)
            } else {
                ProfileUiState.Error(result.exceptionOrNull()?.message ?: "Failed to load profile")
            }
        }
    }

    /**
     * Saves edited profile fields back to Firestore.
     * Only updates the fields passed in — does not overwrite entire document.
     *
     * Usage: call with a map of only the changed fields, e.g.:
     *   saveProfile(mapOf("fullName" to "New Name", "phoneNumber" to "+91..."))
     */
    fun saveProfile(updates: Map<String, Any>) {
        val uid = authRepo.currentUser?.uid
        if (uid == null) {
            _saveState.value = SaveState.Error("Not logged in")
            return
        }

        viewModelScope.launch {
            _saveState.value = SaveState.Saving
            val result = userRepo.updateUserProfile(uid, updates)
            if (result.isSuccess) {
                _saveState.value = SaveState.Saved
                loadProfile() // Refresh profile data after save
            } else {
                _saveState.value = SaveState.Error(
                    result.exceptionOrNull()?.message ?: "Save failed"
                )
            }
        }
    }

    fun resetSaveState() {
        _saveState.value = SaveState.Idle
    }
}

sealed class ProfileUiState {
    object Loading : ProfileUiState()
    data class Success(val profile: EnhancedUserProfile) : ProfileUiState()
    data class Error(val message: String) : ProfileUiState()
}

sealed class SaveState {
    object Idle : SaveState()
    object Saving : SaveState()
    object Saved : SaveState()
    data class Error(val message: String) : SaveState()
}
