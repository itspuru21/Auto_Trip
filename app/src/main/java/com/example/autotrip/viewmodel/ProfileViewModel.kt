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

    private val _deleteState = MutableStateFlow<DeleteState>(DeleteState.Idle)
    val deleteState: StateFlow<DeleteState> = _deleteState

    init {
        loadProfile()
    }

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
     * Permanently deletes the Firestore profile document AND
     * the Firebase Auth account. After this the user must sign up again.
     */
    fun deleteAccount() {
        val uid = authRepo.currentUser?.uid
        if (uid == null) {
            _deleteState.value = DeleteState.Error("Not logged in")
            return
        }
        viewModelScope.launch {
            _deleteState.value = DeleteState.Deleting
            // 1. Delete Firestore document
            val firestoreResult = userRepo.deleteUserProfile(uid)
            if (firestoreResult.isFailure) {
                _deleteState.value = DeleteState.Error(
                    firestoreResult.exceptionOrNull()?.message ?: "Failed to delete profile data"
                )
                return@launch
            }
            // 2. Delete Firebase Auth account
            val authResult = authRepo.deleteAccount()
            _deleteState.value = if (authResult.isSuccess) {
                DeleteState.Deleted
            } else {
                DeleteState.Error(
                    authResult.exceptionOrNull()?.message ?: "Failed to delete account"
                )
            }
        }
    }

    fun resetSaveState() { _saveState.value = SaveState.Idle }
    fun resetDeleteState() { _deleteState.value = DeleteState.Idle }
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

sealed class DeleteState {
    object Idle : DeleteState()
    object Deleting : DeleteState()
    object Deleted : DeleteState()
    data class Error(val message: String) : DeleteState()
}
