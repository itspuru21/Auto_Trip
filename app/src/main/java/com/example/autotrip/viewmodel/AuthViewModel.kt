package com.example.autotrip.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.autotrip.repository.FirebaseAuthRepository
import com.example.autotrip.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Manages authentication state for AuthScreen.
 * Exposes uiState as a StateFlow so the Composable reacts to changes.
 */
class AuthViewModel : ViewModel() {

    private val authRepo = FirebaseAuthRepository()
    private val userRepo = UserRepository()

    // ---- UI State ----
    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState

    /** Check on app start if session already exists — skip auth screen */
    val isAlreadyLoggedIn: Boolean
        get() = authRepo.isLoggedIn

    /**
     * Called when user taps Login.
     * On success → emits AuthUiState.Success
     */
    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState.Error("Email and password cannot be empty")
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading

            val result = authRepo.login(email.trim(), password)
            _uiState.value = if (result.isSuccess) {
                AuthUiState.Success
            } else {
                AuthUiState.Error(result.exceptionOrNull()?.message ?: "Login failed")
            }
        }
    }

    /**
     * Called when user taps Sign Up.
     * Creates auth account, then writes profile to Firestore.
     */
    fun signUp(fullName: String, email: String, password: String) {
        if (fullName.isBlank() || email.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState.Error("All fields are required")
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading

            // 1. Create Firebase Auth account
            val authResult = authRepo.signUp(email.trim(), password)
            if (authResult.isFailure) {
                _uiState.value = AuthUiState.Error(
                    authResult.exceptionOrNull()?.message ?: "Sign up failed"
                )
                return@launch
            }

            val user = authResult.getOrNull()!!

            // 2. Write profile document to Firestore
            val profileResult = userRepo.createUserProfile(
                uid = user.uid,
                fullName = fullName.trim(),
                email = email.trim()
            )

            _uiState.value = if (profileResult.isSuccess) {
                AuthUiState.Success
            } else {
                AuthUiState.Error("Account created but profile save failed. Please update profile later.")
            }
        }
    }

    /** Logout and reset state */
    fun logout() {
        authRepo.logout()
        _uiState.value = AuthUiState.Idle
    }

    /** Reset state (e.g. after navigating away from error) */
    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }
}

/** Sealed class representing all possible UI states for auth */
sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    object Success : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}
