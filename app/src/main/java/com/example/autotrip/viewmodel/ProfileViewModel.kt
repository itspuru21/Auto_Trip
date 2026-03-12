package com.example.autotrip.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.autotrip.model.EnhancedUserProfile
import com.example.autotrip.repository.FirebaseAuthRepository
import com.example.autotrip.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ProfileViewModel : ViewModel() {

    private val authRepo = FirebaseAuthRepository()
    private val userRepo = UserRepository()

    private val _profileState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val profileState: StateFlow<ProfileUiState> = _profileState

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState

    // Only drives the loading spinner in the delete dialog — NOT used for navigation
    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting

    init { loadProfile() }

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
                loadProfile()
            } else {
                _saveState.value = SaveState.Error(
                    result.exceptionOrNull()?.message ?: "Save failed"
                )
            }
        }
    }

    /**
     * Deletes Firestore data + Firebase Auth account, clears local session.
     * Navigation is handled via [onDeleted] callback — called directly when done.
     * We do NOT use a StateFlow for navigation to avoid LaunchedEffect timing issues.
     */
    fun deleteAccount(onDeleted: () -> Unit, onError: (String) -> Unit) {
        val uid = authRepo.currentUser?.uid
        if (uid == null) { onError("Not logged in"); return }

        viewModelScope.launch {
            _isDeleting.value = true

            val firestoreResult = userRepo.deleteUserProfile(uid)
            if (firestoreResult.isFailure) {
                _isDeleting.value = false
                onError(firestoreResult.exceptionOrNull()?.message ?: "Failed to delete data")
                return@launch
            }

            val authResult = authRepo.deleteAccount()
            if (authResult.isFailure) {
                _isDeleting.value = false
                onError(authResult.exceptionOrNull()?.message ?: "Failed to delete account")
                return@launch
            }

            authRepo.logout()          // clear local session token
            _isDeleting.value = false
            onDeleted()                // fire nav callback directly — no StateFlow lag
        }
    }

    fun resetSaveState() { _saveState.value = SaveState.Idle }
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
