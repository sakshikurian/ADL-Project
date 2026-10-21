package com.ecotracker.data.firebase

import android.content.Context
import android.util.Log
import com.ecotracker.data.EcoState
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FirestoreService(private val context: Context) {

    private val tag = "FirestoreService"

    private val firestore: FirebaseFirestore?
        get() = try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                FirebaseFirestore.getInstance()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(tag, "Firebase is not configured or unavailable: ${e.message}")
            null
        }

    fun isAvailable(): Boolean = firestore != null

    /**
     * Saves or updates the primary user profile in users/{userId}
     */
    fun saveUserProfile(
        userId: String,
        name: String,
        email: String,
        state: EcoState,
        notificationsEnabled: Boolean,
        reminderHour: Int,
        reminderMinute: Int,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val db = firestore ?: run {
            Log.d(tag, "Firestore unavailable, skipped cloud profile sync")
            onComplete?.invoke(false)
            return
        }

        val data = hashMapOf(
            "name" to name,
            "email" to email,
            "ecoScore" to state.ecoScore,
            "streak" to state.streakDays,
            "totalCO2Saved" to state.co2SavedKg,
            "totalWaterSaved" to state.waterSavedLitres,
            "weeklyActivities" to state.weeklyActivities,
            "challengeJoined" to state.challengeJoined,
            "progressCompleted" to state.progressCompleted,
            "progressTotal" to state.progressTotal,
            "notificationsEnabled" to notificationsEnabled,
            "reminderHour" to reminderHour,
            "reminderMinute" to reminderMinute,
            "lastUpdated" to FieldValue.serverTimestamp()
        )

        try {
            db.collection("users").document(userId)
                .set(data, SetOptions.merge())
                .addOnSuccessListener {
                    Log.d(tag, "Successfully synced user data to Firestore for $userId")
                    onComplete?.invoke(true)
                }
                .addOnFailureListener { e ->
                    Log.w(tag, "Failed to sync user data to Firestore: ${e.message}")
                    onComplete?.invoke(false)
                }
        } catch (e: Exception) {
            Log.w(tag, "Exception during Firestore saveUserProfile: ${e.message}")
            onComplete?.invoke(false)
        }
    }

    /**
     * Updates notification and reminder preferences in users/{userId}
     */
    fun updateNotificationPreferences(
        userId: String,
        notificationsEnabled: Boolean,
        reminderHour: Int,
        reminderMinute: Int,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val db = firestore ?: run {
            onComplete?.invoke(false)
            return
        }

        val updates = hashMapOf<String, Any>(
            "notificationsEnabled" to notificationsEnabled,
            "reminderHour" to reminderHour,
            "reminderMinute" to reminderMinute,
            "lastUpdated" to FieldValue.serverTimestamp()
        )

        try {
            db.collection("users").document(userId)
                .set(updates, SetOptions.merge())
                .addOnSuccessListener {
                    Log.d(tag, "Successfully updated notification preferences for $userId")
                    onComplete?.invoke(true)
                }
                .addOnFailureListener { e ->
                    Log.w(tag, "Failed to update notification preferences: ${e.message}")
                    onComplete?.invoke(false)
                }
        } catch (e: Exception) {
            Log.w(tag, "Exception during updateNotificationPreferences: ${e.message}")
            onComplete?.invoke(false)
        }
    }

    /**
     * Updates eco scores and streak in users/{userId}
     */
    fun updateEcoState(
        userId: String,
        state: EcoState,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val db = firestore ?: run {
            onComplete?.invoke(false)
            return
        }

        val updates = hashMapOf(
            "ecoScore" to state.ecoScore,
            "streak" to state.streakDays,
            "totalCO2Saved" to state.co2SavedKg,
            "totalWaterSaved" to state.waterSavedLitres,
            "weeklyActivities" to state.weeklyActivities,
            "challengeJoined" to state.challengeJoined,
            "progressCompleted" to state.progressCompleted,
            "progressTotal" to state.progressTotal,
            "lastUpdated" to FieldValue.serverTimestamp()
        )

        try {
            db.collection("users").document(userId)
                .set(updates, SetOptions.merge())
                .addOnSuccessListener {
                    onComplete?.invoke(true)
                }
                .addOnFailureListener { e ->
                    Log.w(tag, "Failed to update eco state: ${e.message}")
                    onComplete?.invoke(false)
                }
        } catch (e: Exception) {
            Log.w(tag, "Exception during updateEcoState: ${e.message}")
            onComplete?.invoke(false)
        }
    }

    /**
     * Records a new activity entry into users/{userId}/activities/{activityId}
     */
    fun recordActivity(
        userId: String,
        activityType: String,
        title: String,
        description: String,
        points: Int,
        co2Saved: Double,
        waterSaved: Int,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val db = firestore ?: run {
            onComplete?.invoke(false)
            return
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val activityData = hashMapOf(
            "activityType" to activityType,
            "title" to title,
            "description" to description,
            "date" to dateFormat.format(Date()),
            "timestamp" to FieldValue.serverTimestamp(),
            "points" to points,
            "co2Saved" to co2Saved,
            "waterSaved" to waterSaved
        )

        try {
            db.collection("users").document(userId)
                .collection("activities")
                .add(activityData)
                .addOnSuccessListener { ref ->
                    Log.d(tag, "Activity logged in Firestore with ID: ${ref.id}")
                    onComplete?.invoke(true)
                }
                .addOnFailureListener { e ->
                    Log.w(tag, "Failed to record activity in Firestore: ${e.message}")
                    onComplete?.invoke(false)
                }
        } catch (e: Exception) {
            Log.w(tag, "Exception during recordActivity: ${e.message}")
            onComplete?.invoke(false)
        }
    }

    /**
     * Fetches user profile data from Firestore if online and returns it
     */
    /**
     * Fetches user profile data from Firestore if online and returns it
     */
    fun fetchUserData(
        userId: String,
        callback: (Map<String, Any>?) -> Unit
    ) {
        val db = firestore ?: run {
            callback(null)
            return
        }

        try {
            db.collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener { documentSnapshot ->
                    if (documentSnapshot.exists()) {
                        callback(documentSnapshot.data)
                    } else {
                        callback(null)
                    }
                }
                .addOnFailureListener { e ->
                    Log.w(
                        tag,
                        "Failed to fetch user data: ${e.message}"
                    )
                    callback(null)
                }
        } catch (e: Exception) {
            Log.w(
                tag,
                "Exception during fetchUserData: ${e.message}"
            )
            callback(null)
        }
    }

    /**
     * Fetches all activities belonging to the user
     */
    fun fetchActivities(
        userId: String,
        callback: (List<Map<String, Any>>) -> Unit
    ) {
        val db = firestore ?: run {
            callback(emptyList())
            return
        }

        try {
            db.collection("users")
                .document(userId)
                .collection("activities")
                .get()
                .addOnSuccessListener { snapshot ->
                    val activities = snapshot.documents.mapNotNull { it.data }

                    Log.d(
                        tag,
                        "Fetched ${activities.size} activities for user $userId"
                    )

                    callback(activities)
                }
                .addOnFailureListener { e ->
                    Log.w(
                        tag,
                        "Failed to fetch activities: ${e.message}"
                    )

                    callback(emptyList())
                }
        } catch (e: Exception) {
            Log.w(
                tag,
                "Exception during fetchActivities: ${e.message}"
            )

            callback(emptyList())
        }
    }
}
