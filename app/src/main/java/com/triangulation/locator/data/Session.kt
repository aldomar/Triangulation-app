package com.triangulation.locator.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A group of bearings taken of the same object, e.g. "Smoke north of town". */
@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** When the session was started, milliseconds since 1970 (UTC). */
    val createdMillis: Long,
)

/** One line of the History list. Filled in by [TriangulationDao.sessionSummaries]. */
data class SessionSummary(
    val id: Long,
    val name: String,
    val createdMillis: Long,
    val bearingCount: Int,
    /** How many bearings are ticked (used for the fix). */
    val includedCount: Int,
    /** Time of the newest bearing, or null if the session is empty. */
    val lastMillis: Long?,
)
