package com.ecotracker.data

import android.content.Context
import android.content.SharedPreferences
import com.ecotracker.data.database.EcoDatabase
import com.ecotracker.data.firebase.FirebaseAuthService
import com.ecotracker.data.firebase.FirestoreService
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log

class LocalEcoRepository(context: Context) : EcoRepository {

    private val appContext = context.applicationContext
    private val database = EcoDatabase.getDatabase(appContext)
    private val userDao = database.userDao()
    private val activityDao = database.activityDao()
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val firestoreService = FirestoreService(appContext)
    private val authService = FirebaseAuthService(appContext)

    override fun getState(): EcoState = EcoState(
        ecoScore = prefs.getInt(KEY_SCORE, DEFAULT_SCORE),
        ecoScoreMax = DEFAULT_SCORE_MAX,
        scoreDeltaThisWeek = prefs.getInt(KEY_SCORE_DELTA, DEFAULT_SCORE_DELTA),
        weeklyActivities = prefs.getInt(KEY_ACTIVITIES, DEFAULT_ACTIVITIES),
        co2SavedKg = prefs.getFloat(KEY_CO2, DEFAULT_CO2).toDouble(),
        waterSavedLitres = prefs.getInt(KEY_WATER, DEFAULT_WATER),
        streakDays = prefs.getInt(KEY_STREAK, DEFAULT_STREAK),
        challengeJoined = prefs.getBoolean(KEY_CHALLENGE_JOINED, false),
        progressCompleted = prefs.getInt(KEY_PROGRESS_DONE, DEFAULT_PROGRESS_DONE),
        progressTotal = prefs.getInt(KEY_PROGRESS_TOTAL, DEFAULT_PROGRESS_TOTAL)
    )

    override fun joinChallenge(): EcoState {
        val current = getState()
        if (current.challengeJoined) return current

        val updated = current.copy(
            challengeJoined = true,
            ecoScore = (current.ecoScore + CHALLENGE_JOIN_SCORE_BUMP).coerceAtMost(current.ecoScoreMax),
            weeklyActivities = current.weeklyActivities + 1,
            progressCompleted = (current.progressCompleted + 1).coerceAtMost(current.progressTotal)
        )
        save(updated)

        // Sync with Firestore in background
        getEffectiveUserId()?.let { uid ->
            firestoreService.updateEcoState(uid, updated)
        }

        return updated
    }

    override fun logActivity(activity: LoggableActivity): EcoState {
        val current = getState()
        val updated = current.copy(
            ecoScore = (current.ecoScore + (activity.points / POINTS_TO_SCORE_DIVISOR).coerceAtLeast(1))
                .coerceAtMost(current.ecoScoreMax),
            weeklyActivities = current.weeklyActivities + 1,
            co2SavedKg = current.co2SavedKg + activity.co2Kg,
            waterSavedLitres = current.waterSavedLitres + activity.waterLitres,
            progressCompleted = (current.progressCompleted + 1).coerceAtMost(current.progressTotal)
        )
        save(updated)
        val userId = getEffectiveUserId()

        if (userId != null) {
            val activityEntity = com.ecotracker.data.database.ActivityEntity(
                firebaseUid = userId,
                title = activity.label,
                points = activity.points,
                co2SavedKg = activity.co2Kg,
                waterSavedLitres = activity.waterLitres
            )

            kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.Dispatchers.IO
            ).launch {
                activityDao.insertActivity(activityEntity)
            }
        }

        // Sync with Firestore in background
        getEffectiveUserId()?.let { uid ->
            firestoreService.updateEcoState(uid, updated)
            firestoreService.recordActivity(
                userId = uid,
                activityType = "QUICK_LOG",
                title = activity.label,
                description = "Logged eco-friendly action",
                points = activity.points,
                co2Saved = activity.co2Kg,
                waterSaved = activity.waterLitres
            )
        }

        return updated
    }

    override fun getActivityHistory(): List<com.ecotracker.data.database.ActivityEntity> {
        val userId = getEffectiveUserId() ?: return emptyList()

        return kotlinx.coroutines.runBlocking {
            activityDao.getActivities(userId)
        }
    }

    override fun isLoggedIn(): Boolean {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    override fun getUserProfile(): UserProfile? {
        if (!isLoggedIn()) return null
        val savedName = prefs.getString(KEY_USER_NAME, null)
        val savedEmail = prefs.getString(KEY_USER_EMAIL, null)
        val fallbackName = if (!savedEmail.isNullOrEmpty()) {
            savedEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
        } else {
            "Eco Friend"
        }

        return UserProfile(
            email = savedEmail ?: "user@ecotracker.org",
            name = if (!savedName.isNullOrBlank()) savedName else fallbackName,
            joinDate = prefs.getString(KEY_USER_JOIN_DATE, "September 2026") ?: "September 2026"
        )
    }

    override fun login(email: String, name: String?): Boolean {
        val userName = name ?: if (email.contains("@")) {
            email.substringBefore("@").replaceFirstChar { it.uppercase() }
        } else {
            "Eco Enthusiast"
        }

        prefs.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, true)
            putString(KEY_USER_EMAIL, email)
            putString(KEY_USER_NAME, userName)
            if (!prefs.contains(KEY_USER_JOIN_DATE)) {
                putString(KEY_USER_JOIN_DATE, "September 2026")
            }
            apply()
        }
        return true
    }

    override fun register(name: String, email: String): Boolean {
        prefs.edit().clear().apply()
        val success = login(email, name)

        if (success) {
            val userId = getEffectiveUserId() ?: email

            val zeroState = EcoState(
                ecoScore = 0,
                ecoScoreMax = 100,
                scoreDeltaThisWeek = 0,
                weeklyActivities = 0,
                co2SavedKg = 0.0,
                waterSavedLitres = 0,
                streakDays = 0,
                challengeJoined = false,
                progressCompleted = 0,
                progressTotal = 5
            )
            save(zeroState)

            val user = com.ecotracker.data.database.UserEntity(
                firebaseUid = userId,
                name = name,
                email = email,
                joinDate = getUserProfile()?.joinDate ?: "September 2026",
                ecoScore = 0,
                ecoScoreMax = 100,
                scoreDeltaThisWeek = 0,
                weeklyActivities = 0,
                co2SavedKg = 0.0,
                waterSavedLitres = 0,
                streakDays = 0,
                challengeJoined = false,
                progressCompleted = 0,
                progressTotal = 5,
                notificationsEnabled = areNotificationsEnabled(),
                reminderHour = DEFAULT_REMINDER_HOUR,
                reminderMinute = DEFAULT_REMINDER_MINUTE
            )

            kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.Dispatchers.IO
            ).launch {
                userDao.insertUser(user)
                activityDao.deleteActivities(userId)
            }
        }

        return success
    }

    override fun loginWithFirebase(
        email: String,
        password: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        if (authService.isAvailable()) {
            authService.signIn(email, password) { success, uid, errorMsg ->
                if (success && uid != null) {
                    prefs.edit().clear().apply()
                    prefs.edit().putString(KEY_FIREBASE_UID, uid).apply()
                    login(email)
                    fetchAndHydrateFromFirestore(uid) {
                        onComplete(true, null)
                    }
                } else {
                    val isFallbackReason = errorMsg?.contains("network", ignoreCase = true) == true ||
                            errorMsg?.contains("disabled", ignoreCase = true) == true ||
                            errorMsg?.contains("configuration", ignoreCase = true) == true ||
                            errorMsg?.contains("unavailable", ignoreCase = true) == true

                    if (isFallbackReason) {
                        prefs.edit().clear().apply()
                        login(email)
                        val effectiveUid = getEffectiveUserId() ?: email
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            val localUser = userDao.getUser(effectiveUid)
                            if (localUser != null) {
                                prefs.edit().apply {
                                    putString(KEY_USER_NAME, localUser.name)
                                    putInt(KEY_SCORE, localUser.ecoScore)
                                    putInt(KEY_STREAK, localUser.streakDays)
                                    putFloat(KEY_CO2, localUser.co2SavedKg.toFloat())
                                    putInt(KEY_WATER, localUser.waterSavedLitres)
                                    putInt(KEY_ACTIVITIES, localUser.weeklyActivities)
                                    putBoolean(KEY_CHALLENGE_JOINED, localUser.challengeJoined)
                                    putInt(KEY_PROGRESS_DONE, localUser.progressCompleted)
                                    putInt(KEY_PROGRESS_TOTAL, localUser.progressTotal)
                                    putBoolean(KEY_NOTIFICATIONS_ENABLED, localUser.notificationsEnabled)
                                    putInt(KEY_REMINDER_HOUR, localUser.reminderHour)
                                    putInt(KEY_REMINDER_MINUTE, localUser.reminderMinute)
                                    apply()
                                }
                            }
                            withContext(kotlinx.coroutines.Dispatchers.Main) {
                                onComplete(true, null)
                            }
                        }
                    } else {
                        onComplete(false, errorMsg)
                    }
                }
            }
        } else {
            prefs.edit().clear().apply()
            login(email)
            onComplete(true, null)
        }
    }

    override fun registerWithFirebase(
        name: String,
        email: String,
        password: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        if (authService.isAvailable()) {
            authService.signUp(name, email, password) { success, uid, errorMsg ->
                if (success && uid != null) {
                    prefs.edit().clear().apply()
                    prefs.edit().putString(KEY_FIREBASE_UID, uid).apply()
                    login(email, name)

                    // Brand new user starts with 0s strictly
                    val zeroState = EcoState(
                        ecoScore = 0,
                        ecoScoreMax = 100,
                        scoreDeltaThisWeek = 0,
                        weeklyActivities = 0,
                        co2SavedKg = 0.0,
                        waterSavedLitres = 0,
                        streakDays = 0,
                        challengeJoined = false,
                        progressCompleted = 0,
                        progressTotal = 5
                    )
                    save(zeroState)

                    val userEntity = com.ecotracker.data.database.UserEntity(
                        firebaseUid = uid,
                        name = name,
                        email = email,
                        joinDate = "September 2026",
                        ecoScore = 0,
                        ecoScoreMax = 100,
                        scoreDeltaThisWeek = 0,
                        weeklyActivities = 0,
                        co2SavedKg = 0.0,
                        waterSavedLitres = 0,
                        streakDays = 0,
                        challengeJoined = false,
                        progressCompleted = 0,
                        progressTotal = 5,
                        notificationsEnabled = false,
                        reminderHour = DEFAULT_REMINDER_HOUR,
                        reminderMinute = DEFAULT_REMINDER_MINUTE
                    )

                    kotlinx.coroutines.CoroutineScope(
                        kotlinx.coroutines.Dispatchers.IO
                    ).launch {
                        userDao.insertUser(userEntity)
                        activityDao.deleteActivities(uid)
                    }

                    firestoreService.saveUserProfile(
                        userId = uid,
                        name = name,
                        email = email,
                        state = zeroState,
                        notificationsEnabled = false,
                        reminderHour = DEFAULT_REMINDER_HOUR,
                        reminderMinute = DEFAULT_REMINDER_MINUTE,
                        onComplete = {
                            onComplete(true, null)
                        }
                    )
                } else {
                    val isFallbackReason = errorMsg?.contains("network", ignoreCase = true) == true ||
                            errorMsg?.contains("disabled", ignoreCase = true) == true ||
                            errorMsg?.contains("configuration", ignoreCase = true) == true ||
                            errorMsg?.contains("unavailable", ignoreCase = true) == true

                    if (isFallbackReason) {
                        register(name, email)
                        onComplete(true, null)
                    } else {
                        onComplete(false, errorMsg)
                    }
                }
            }
        } else {
            register(name, email)
            onComplete(true, null)
        }
    }

    override fun getFirebaseUserId(): String? {
        return authService.getCurrentUserId() ?: prefs.getString(KEY_FIREBASE_UID, null)
    }

    /**
     * Effective user ID for Firestore: uses Firebase UID, or fallback to email hash
     */
    private fun getEffectiveUserId(): String? {
        val fbUid = getFirebaseUserId()
        if (!fbUid.isNullOrBlank()) return fbUid
        val email = prefs.getString(KEY_USER_EMAIL, null) ?: return null
        return email.replace(".", "_").replace("@", "_at_")
    }

    override fun areNotificationsEnabled(): Boolean {
        return prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, false)
    }

    override fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply()
        getEffectiveUserId()?.let { uid ->
            val (hour, minute) = getReminderTime()
            firestoreService.updateNotificationPreferences(uid, enabled, hour, minute)
        }
    }

    override fun getReminderTime(): Pair<Int, Int> {
        val hour = prefs.getInt(KEY_REMINDER_HOUR, DEFAULT_REMINDER_HOUR)
        val minute = prefs.getInt(KEY_REMINDER_MINUTE, DEFAULT_REMINDER_MINUTE)
        return Pair(hour, minute)
    }

    override fun setReminderTime(hour: Int, minute: Int) {
        prefs.edit()
            .putInt(KEY_REMINDER_HOUR, hour)
            .putInt(KEY_REMINDER_MINUTE, minute)
            .apply()
        getEffectiveUserId()?.let { uid ->
            firestoreService.updateNotificationPreferences(uid, areNotificationsEnabled(), hour, minute)
        }
    }

    override fun syncWithFirestore(onComplete: ((Boolean) -> Unit)?) {
        val uid = getEffectiveUserId() ?: run {
            onComplete?.invoke(false)
            return
        }
        val profile = getUserProfile()
        val state = getState()
        val (hour, minute) = getReminderTime()
        firestoreService.saveUserProfile(
            userId = uid,
            name = profile?.name ?: "Eco Friend",
            email = profile?.email ?: "user@ecotracker.org",
            state = state,
            notificationsEnabled = areNotificationsEnabled(),
            reminderHour = hour,
            reminderMinute = minute,
            onComplete = onComplete
        )
    }

    private fun fetchAndHydrateFromFirestore(userId: String, onDone: (() -> Unit)? = null) {
        // 1. Fetch the user's profile data
        firestoreService.fetchUserData(userId) { data ->
            if (data != null) {
                val name = (data["name"] as? String) ?: prefs.getString(KEY_USER_NAME, "Eco Friend") ?: "Eco Friend"
                val email = (data["email"] as? String) ?: prefs.getString(KEY_USER_EMAIL, "") ?: ""
                val ecoScore = (data["ecoScore"] as? Long)?.toInt() ?: 0
                val streak = (data["streak"] as? Long)?.toInt() ?: 0
                val co2 = (data["totalCO2Saved"] as? Double) ?: 0.0
                val water = (data["totalWaterSaved"] as? Long)?.toInt() ?: 0
                val weeklyActivities = (data["weeklyActivities"] as? Long)?.toInt() ?: 0
                val challengeJoined = (data["challengeJoined"] as? Boolean) ?: false
                val progressCompleted = (data["progressCompleted"] as? Long)?.toInt() ?: 0
                val progressTotal = (data["progressTotal"] as? Long)?.toInt() ?: 5
                val notificationsEnabled = (data["notificationsEnabled"] as? Boolean) ?: false
                val reminderHour = (data["reminderHour"] as? Long)?.toInt() ?: DEFAULT_REMINDER_HOUR
                val reminderMinute = (data["reminderMinute"] as? Long)?.toInt() ?: DEFAULT_REMINDER_MINUTE

                prefs.edit().apply {
                    putString(KEY_USER_NAME, name)
                    if (email.isNotBlank()) putString(KEY_USER_EMAIL, email)
                    putInt(KEY_SCORE, ecoScore)
                    putInt(KEY_STREAK, streak)
                    putFloat(KEY_CO2, co2.toFloat())
                    putInt(KEY_WATER, water)
                    putInt(KEY_ACTIVITIES, weeklyActivities)
                    putBoolean(KEY_CHALLENGE_JOINED, challengeJoined)
                    putInt(KEY_PROGRESS_DONE, progressCompleted)
                    putInt(KEY_PROGRESS_TOTAL, progressTotal)
                    putBoolean(KEY_NOTIFICATIONS_ENABLED, notificationsEnabled)
                    putInt(KEY_REMINDER_HOUR, reminderHour)
                    putInt(KEY_REMINDER_MINUTE, reminderMinute)
                    apply()
                }

                // Cache in Room
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    userDao.insertUser(
                        com.ecotracker.data.database.UserEntity(
                            firebaseUid = userId,
                            name = name,
                            email = email,
                            joinDate = "September 2026",
                            ecoScore = ecoScore,
                            ecoScoreMax = 100,
                            scoreDeltaThisWeek = 0,
                            weeklyActivities = weeklyActivities,
                            co2SavedKg = co2,
                            waterSavedLitres = water,
                            streakDays = streak,
                            challengeJoined = challengeJoined,
                            progressCompleted = progressCompleted,
                            progressTotal = progressTotal,
                            notificationsEnabled = notificationsEnabled,
                            reminderHour = reminderHour,
                            reminderMinute = reminderMinute
                        )
                    )
                }
            } else {
                // Cloud data not found or offline: check if already in Room
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    val localUser = userDao.getUser(userId)
                    if (localUser != null) {
                        prefs.edit().apply {
                            putString(KEY_USER_NAME, localUser.name)
                            putInt(KEY_SCORE, localUser.ecoScore)
                            putInt(KEY_STREAK, localUser.streakDays)
                            putFloat(KEY_CO2, localUser.co2SavedKg.toFloat())
                            putInt(KEY_WATER, localUser.waterSavedLitres)
                            putInt(KEY_ACTIVITIES, localUser.weeklyActivities)
                            putBoolean(KEY_CHALLENGE_JOINED, localUser.challengeJoined)
                            putInt(KEY_PROGRESS_DONE, localUser.progressCompleted)
                            putInt(KEY_PROGRESS_TOTAL, localUser.progressTotal)
                            putBoolean(KEY_NOTIFICATIONS_ENABLED, localUser.notificationsEnabled)
                            putInt(KEY_REMINDER_HOUR, localUser.reminderHour)
                            putInt(KEY_REMINDER_MINUTE, localUser.reminderMinute)
                            apply()
                        }
                    }
                }
            }

            // 2. Fetch the user's saved activities from Firestore and cache in Room
            firestoreService.fetchActivities(userId) { activities ->
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    activityDao.deleteActivities(userId)
                    activities.forEach { activity ->
                        val title = activity["title"] as? String ?: "Eco Activity"
                        val points = (activity["points"] as? Long)?.toInt() ?: 0
                        val co2Saved = (activity["co2Saved"] as? Double) ?: 0.0
                        val waterSaved = (activity["waterSaved"] as? Long)?.toInt() ?: 0
                        activityDao.insertActivity(
                            com.ecotracker.data.database.ActivityEntity(
                                firebaseUid = userId,
                                title = title,
                                points = points,
                                co2SavedKg = co2Saved,
                                waterSavedLitres = waterSaved
                            )
                        )
                    }
                    Log.d("LocalEcoRepository", "Loaded ${activities.size} activities from Firestore into Room")
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onDone?.invoke()
                    }
                }
            }
        }
    }
    override fun getCustomActivities(): List<CustomActivity> {
        val raw = prefs.getString(KEY_CUSTOM_ACTIVITIES, null) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return raw.split(";;").mapNotNull { entry ->
            val parts = entry.split("::")
            if (parts.size >= 5) {
                CustomActivity(
                    id = parts[0],
                    name = parts[1],
                    points = parts[2].toIntOrNull() ?: 5,
                    co2Kg = parts[3].toDoubleOrNull() ?: 0.2,
                    waterLitres = parts[4].toIntOrNull() ?: 0
                )
            } else null
        }
    }

    private fun saveCustomActivities(list: List<CustomActivity>) {
        val raw = list.joinToString(";;") { "${it.id}::${it.name}::${it.points}::${it.co2Kg}::${it.waterLitres}" }
        prefs.edit().putString(KEY_CUSTOM_ACTIVITIES, raw).apply()
    }

    override fun addCustomActivity(name: String): EcoState {
        val id = java.util.UUID.randomUUID().toString()
        val custom = CustomActivity(id = id, name = name, points = 5, co2Kg = 0.2, waterLitres = 0)
        val currentList = getCustomActivities().toMutableList()
        currentList.add(custom)
        saveCustomActivities(currentList)
        return logActivity(LoggableActivity(custom.name, custom.points, custom.co2Kg, custom.waterLitres))
    }

    override fun editCustomActivity(id: String, newName: String): EcoState {
        val currentList = getCustomActivities().toMutableList()
        val index = currentList.indexOfFirst { it.id == id }
        if (index != -1) {
            currentList[index] = currentList[index].copy(name = newName)
            saveCustomActivities(currentList)
        }
        return getState()
    }

    override fun deleteCustomActivity(id: String): EcoState {
        val currentList = getCustomActivities().toMutableList()
        val target = currentList.find { it.id == id }
        if (target != null) {
            currentList.remove(target)
            saveCustomActivities(currentList)

            val current = getState()
            val updated = current.copy(
                ecoScore = (current.ecoScore - (target.points / POINTS_TO_SCORE_DIVISOR).coerceAtLeast(1)).coerceAtLeast(0),
                weeklyActivities = (current.weeklyActivities - 1).coerceAtLeast(0),
                co2SavedKg = (current.co2SavedKg - target.co2Kg).coerceAtLeast(0.0),
                waterSavedLitres = (current.waterSavedLitres - target.waterLitres).coerceAtLeast(0),
                progressCompleted = (current.progressCompleted - 1).coerceAtLeast(0)
            )
            save(updated)
            getEffectiveUserId()?.let { uid ->
                firestoreService.updateEcoState(uid, updated)
            }
            return updated
        }
        return getState()
    }

    override fun logout() {
        authService.signOut()
        prefs.edit().clear().apply()
    }

    private fun save(state: EcoState) {
        prefs.edit().apply {
            putInt(KEY_SCORE, state.ecoScore)
            putInt(KEY_SCORE_DELTA, state.scoreDeltaThisWeek)
            putInt(KEY_ACTIVITIES, state.weeklyActivities)
            putFloat(KEY_CO2, state.co2SavedKg.toFloat())
            putInt(KEY_WATER, state.waterSavedLitres)
            putInt(KEY_STREAK, state.streakDays)
            putBoolean(KEY_CHALLENGE_JOINED, state.challengeJoined)
            putInt(KEY_PROGRESS_DONE, state.progressCompleted)
            putInt(KEY_PROGRESS_TOTAL, state.progressTotal)
            apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "eco_tracker_prefs"

        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_JOIN_DATE = "user_join_date"
        private const val KEY_CUSTOM_ACTIVITIES = "custom_activities_list"
        private const val KEY_FIREBASE_UID = "firebase_user_uid"

        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_REMINDER_HOUR = "reminder_hour"
        private const val KEY_REMINDER_MINUTE = "reminder_minute"

        private const val KEY_SCORE = "eco_score"
        private const val KEY_SCORE_DELTA = "eco_score_delta"
        private const val KEY_ACTIVITIES = "weekly_activities"
        private const val KEY_CO2 = "co2_saved_kg"
        private const val KEY_WATER = "water_saved_litres"
        private const val KEY_STREAK = "streak_days"
        private const val KEY_CHALLENGE_JOINED = "challenge_joined"
        private const val KEY_PROGRESS_DONE = "progress_completed"
        private const val KEY_PROGRESS_TOTAL = "progress_total"

        // New accounts start strictly with 0 points, activities, CO2/water savings, and streaks
        private const val DEFAULT_SCORE = 0
        private const val DEFAULT_SCORE_MAX = 100
        private const val DEFAULT_SCORE_DELTA = 0
        private const val DEFAULT_ACTIVITIES = 0
        private const val DEFAULT_CO2 = 0.0f
        private const val DEFAULT_WATER = 0
        private const val DEFAULT_STREAK = 0
        private const val DEFAULT_PROGRESS_DONE = 0
        private const val DEFAULT_PROGRESS_TOTAL = 5

        // Default reminder time: 8:00 PM (20:00)
        private const val DEFAULT_REMINDER_HOUR = 20
        private const val DEFAULT_REMINDER_MINUTE = 0

        private const val CHALLENGE_JOIN_SCORE_BUMP = 2
        private const val POINTS_TO_SCORE_DIVISOR = 10
    }
}
