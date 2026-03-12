package com.example.autotrip.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await

/**
 * Handles all Firebase Authentication operations.
 * Returns Result<T> so the ViewModel can handle success/failure cleanly.
 */
class FirebaseAuthRepository {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    /** Currently logged-in user, or null if not authenticated */
    val currentUser: FirebaseUser?
        get() = auth.currentUser

    /** Returns true if a user session already exists */
    val isLoggedIn: Boolean
        get() = auth.currentUser != null

    /**
     * Sign up with email and password.
     * Returns the new FirebaseUser on success.
     */
    suspend fun signUp(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user ?: return Result.failure(Exception("User creation failed"))
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sign in with email and password.
     * Returns the FirebaseUser on success.
     */
    suspend fun login(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user ?: return Result.failure(Exception("Login failed"))
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sign out the current user.
     */
    fun logout() {
        auth.signOut()
    }
}
