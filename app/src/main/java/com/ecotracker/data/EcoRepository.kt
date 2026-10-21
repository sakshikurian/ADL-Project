package com.ecotracker.data

data class UserProfile(
    val email: String,
    val name: String,
    val joinDate: String
)

data class CustomActivity(
    val id: String,
    val name: String,
    val points: Int = 5,
    val co2Kg: Double = 0.2,
    val waterLitres: Int = 0
)
interface EcoRepository {
    fun getState(): EcoState
    fun joinChallenge(): EcoState
    fun logActivity(activity: LoggableActivity): EcoState
    fun getActivityHistory(): List<com.ecotracker.data.database.ActivityEntity>


    // Custom Activity CRUD
    fun getCustomActivities(): List<CustomActivity>
    fun addCustomActivity(name: String): EcoState
    fun editCustomActivity(id: String, newName: String): EcoState
    fun deleteCustomActivity(id: String): EcoState

    // Authentication & User Profile
    fun isLoggedIn(): Boolean
    fun getUserProfile(): UserProfile?
    fun login(email: String, name: String? = null): Boolean
    fun register(name: String, email: String): Boolean
    fun logout()

    // Notification & Reminder Preferences
    fun areNotificationsEnabled(): Boolean
    fun setNotificationsEnabled(enabled: Boolean)
    fun getReminderTime(): Pair<Int, Int>
    fun setReminderTime(hour: Int, minute: Int)

    // Cloud / Firebase Sync
    fun getFirebaseUserId(): String?
    fun loginWithFirebase(email: String, password: String, onComplete: (Boolean, String?) -> Unit)
    fun registerWithFirebase(name: String, email: String, password: String, onComplete: (Boolean, String?) -> Unit)
    fun syncWithFirestore(onComplete: ((Boolean) -> Unit)? = null)


}

