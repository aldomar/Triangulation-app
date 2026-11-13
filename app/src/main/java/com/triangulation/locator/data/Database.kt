package com.triangulation.locator.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * The app's database: one table of sessions and one of observations, kept in app-private
 * storage on the phone.
 *
 * When the tables change in a later version, raise [version] and add a migration so existing
 * bearings are kept. Room records each version's layout in `app/schemas/`.
 *
 * Versions: 1 = Phase 4. 2 = car mode (observations gained headingFromCar, speedMps,
 * lineUpAgeSeconds; added automatically, existing rows get false/null). 3 = magnetic
 * interference check (observations gained magneticFieldRatio; existing rows get null).
 * 4 = typed-in bearings (observations gained typedIn and note; existing rows get false/null).
 * 5 = compass checks against landmarks (new table compass_checks; nothing else changes).
 * 6 = zoom presets (observations gained zoomRatio; existing rows get null, meaning 1×).
 */
@Database(
    entities = [Session::class, Observation::class, CompassCheck::class],
    version = 6,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
    ],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): TriangulationDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "triangulation.db")
                .build()
                .also { instance = it }
        }
    }
}

@Dao
interface TriangulationDao {
    // ---------- Sessions ----------

    /** Every session with its bearing counts, most recently used first. */
    @Query(
        """
        SELECT s.id, s.name, s.createdMillis,
               COUNT(o.id) AS bearingCount,
               COALESCE(SUM(o.included), 0) AS includedCount,
               MAX(o.timeMillis) AS lastMillis
        FROM sessions s LEFT JOIN observations o ON o.sessionId = s.id
        GROUP BY s.id
        ORDER BY COALESCE(MAX(o.timeMillis), s.createdMillis) DESC
        """
    )
    fun sessionSummaries(): Flow<List<SessionSummary>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun session(id: Long): Session?

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun sessionFlow(id: Long): Flow<Session?>

    @Query("SELECT * FROM sessions ORDER BY id DESC")
    fun sessionsFlow(): Flow<List<Session>>

    /** The session whose newest bearing (or creation, if empty) is most recent. */
    @Query(
        """
        SELECT s.* FROM sessions s LEFT JOIN observations o ON o.sessionId = s.id
        GROUP BY s.id
        ORDER BY COALESCE(MAX(o.timeMillis), s.createdMillis) DESC
        LIMIT 1
        """
    )
    suspend fun mostRecentSession(): Session?

    @Insert
    suspend fun insertSession(session: Session): Long

    @Query("UPDATE sessions SET name = :name WHERE id = :id")
    suspend fun renameSession(id: Long, name: String)

    /** Also deletes the session's observations (foreign key cascade). Photos are removed separately. */
    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    // ---------- Observations ----------

    @Query("SELECT * FROM observations WHERE sessionId = :sessionId ORDER BY timeMillis DESC")
    fun observationsFlow(sessionId: Long): Flow<List<Observation>>

    @Query("SELECT * FROM observations WHERE sessionId = :sessionId ORDER BY timeMillis DESC")
    suspend fun observations(sessionId: Long): List<Observation>

    @Query("SELECT photoFileName FROM observations WHERE sessionId = :sessionId")
    suspend fun photoFileNames(sessionId: Long): List<String>

    @Query("SELECT COUNT(*) FROM observations WHERE id = :id")
    suspend fun countWithId(id: String): Int

    @Insert
    suspend fun insertObservation(observation: Observation)

    @Insert
    suspend fun insertObservations(observations: List<Observation>)

    @Query("UPDATE observations SET included = :included WHERE id = :id")
    suspend fun setIncluded(id: String, included: Boolean)

    @Query("UPDATE observations SET sessionId = :sessionId WHERE id = :id")
    suspend fun moveObservation(id: String, sessionId: Long)

    @Query("DELETE FROM observations WHERE id = :id")
    suspend fun deleteObservation(id: String)

    // ---------- Compass checks ----------

    @Query("SELECT * FROM compass_checks ORDER BY timeMillis DESC")
    fun compassChecksFlow(): Flow<List<CompassCheck>>

    @Insert
    suspend fun insertCompassCheck(check: CompassCheck): Long

    @Query("DELETE FROM compass_checks WHERE id = :id")
    suspend fun deleteCompassCheck(id: Long)
}
