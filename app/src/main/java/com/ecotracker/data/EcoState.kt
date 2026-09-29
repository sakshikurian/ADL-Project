package com.ecotracker.data

/**
 * Everything the Home screen needs to render. Kept as one flat, serializable-friendly
 * shape so a future [EcoRepository] implementation (e.g. backed by Firebase Firestore)
 * can produce/consume the exact same object the UI already knows how to draw.
 */
data class EcoState(
    val ecoScore: Int,
    val ecoScoreMax: Int,
    val scoreDeltaThisWeek: Int,
    val weeklyActivities: Int,
    val co2SavedKg: Double,
    val waterSavedLitres: Int,
    val streakDays: Int,
    val challengeJoined: Boolean,
    val progressCompleted: Int,
    val progressTotal: Int
)

/** A single quick-log option shown in the "Log Activity" picker. */
data class LoggableActivity(
    val label: String,
    val points: Int,
    val co2Kg: Double,
    val waterLitres: Int
)
