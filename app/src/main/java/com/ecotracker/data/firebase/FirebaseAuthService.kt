package com.ecotracker.data.firebase

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest

class FirebaseAuthService(private val context: Context) {

    private val tag = "FirebaseAuthService"

    private val auth: FirebaseAuth?
        get() = try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                FirebaseAuth.getInstance()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(tag, "Firebase Auth not available: ${e.message}")
            null
        }

    fun isAvailable(): Boolean = auth != null

    fun getCurrentUserId(): String? = try {
        auth?.currentUser?.uid
    } catch (e: Exception) {
        null
    }

    fun getCurrentUserEmail(): String? = try {
        auth?.currentUser?.email
    } catch (e: Exception) {
        null
    }

    fun signIn(
        email: String,
        password: String,
        onResult: (success: Boolean, userId: String?, errorMsg: String?) -> Unit
    ) {
        val firebaseAuth = auth ?: run {
            onResult(false, null, "Firebase Authentication is not configured yet.")
            return
        }

        try {
            firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { authResult ->
                    val uid = authResult.user?.uid
                    onResult(true, uid, null)
                }
                .addOnFailureListener { exception ->
                    Log.w(tag, "Firebase signIn failed: ${exception.message}")
                    onResult(false, null, exception.localizedMessage ?: "Sign in failed")
                }
        } catch (e: Exception) {
            Log.w(tag, "Exception during Firebase signIn: ${e.message}")
            onResult(false, null, e.localizedMessage ?: "Authentication error")
        }
    }

    fun signUp(
        name: String,
        email: String,
        password: String,
        onResult: (success: Boolean, userId: String?, errorMsg: String?) -> Unit
    ) {
        val firebaseAuth = auth ?: run {
            onResult(false, null, "Firebase Authentication is not configured yet.")
            return
        }

        try {
            firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener { authResult ->
                    val user = authResult.user
                    val uid = user?.uid
                    // Set display name in Firebase Auth
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(name)
                        .build()
                    user?.updateProfile(profileUpdates)
                    onResult(true, uid, null)
                }
                .addOnFailureListener { exception ->
                    Log.w(tag, "Firebase signUp failed: ${exception.message}")
                    onResult(false, null, exception.localizedMessage ?: "Account creation failed")
                }
        } catch (e: Exception) {
            Log.w(tag, "Exception during Firebase signUp: ${e.message}")
            onResult(false, null, e.localizedMessage ?: "Registration error")
        }
    }

    fun signOut() {
        try {
            auth?.signOut()
        } catch (e: Exception) {
            Log.w(tag, "Exception during Firebase signOut: ${e.message}")
        }
    }
}
