package com.ecotracker.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Local, fully-functional stand-in for a real backend. Persists to SharedPreferences
 * so "Join Challenge" and "Log Activity" have a lasting effect across app restarts,
 * without requiring Firebase, a database, or any networking.
 */
class LocalEcoRepository(context: Context) : EcoRepository {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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
        return updated
    }

    override fun isLoggedIn(): Boolean {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    override fun getUserProfile(): UserProfile? {
        if (!isLoggedIn()) return null
        val savedName = prefs.getString(KEY_USER_NAME, null)
        val savedEmail = prefs.getString(KEY_USER_EMAIL, null)
        val fallbackName = if (!savedEmail.isNullOrEmpty()) savedEmail.substringBefore("@").replaceFirstChar { it.uppercase() } else "Eco Friend"

        return UserProfile(
            email = savedEmail ?: "user@ecotracker.org",
            name = if (!savedName.isNullOrBlank()) savedName else fallbackName,
            joinDate = prefs.getString(KEY_USER_JOIN_DATE, "September 2026") ?: "September 2026"
        )
    }

    override fun login(email: String, name: String?): Boolean {
        val userName = name ?: if (email.contains("@")) email.substringBefore("@").capitalize() else "Eco Enthusiast"
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
        return login(email, name)
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
            return updated
        }
        return getState()
    }

    override fun logout() {
        prefs.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, false)
            apply()
        }
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

        private const val KEY_SCORE = "eco_score"
        private const val KEY_SCORE_DELTA = "eco_score_delta"
        private const val KEY_ACTIVITIES = "weekly_activities"
        private const val KEY_CO2 = "co2_saved_kg"
        private const val KEY_WATER = "water_saved_litres"
        private const val KEY_STREAK = "streak_days"
        private const val KEY_CHALLENGE_JOINED = "challenge_joined"
        private const val KEY_PROGRESS_DONE = "progress_completed"
        private const val KEY_PROGRESS_TOTAL = "progress_total"

        // Mock seed values matching the original design spec.
        private const val DEFAULT_SCORE = 82
        private const val DEFAULT_SCORE_MAX = 100
        private const val DEFAULT_SCORE_DELTA = 8
        private const val DEFAULT_ACTIVITIES = 24
        private const val DEFAULT_CO2 = 12.4f
        private const val DEFAULT_WATER = 1850
        private const val DEFAULT_STREAK = 5
        private const val DEFAULT_PROGRESS_DONE = 3
        private const val DEFAULT_PROGRESS_TOTAL = 5

        private const val CHALLENGE_JOIN_SCORE_BUMP = 2
        private const val POINTS_TO_SCORE_DIVISOR = 10
    }
}
