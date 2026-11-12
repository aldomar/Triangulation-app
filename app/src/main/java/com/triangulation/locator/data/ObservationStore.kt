package com.triangulation.locator.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves sessions and observations in the app's private storage: details in a small database,
 * photos as JPEG files in `filesDir/observations`. Other apps and the Gallery can't see these,
 * and uninstalling the app removes them.
 */
class ObservationStore(context: Context) {
    private val appContext = context.applicationContext
    private val db = AppDatabase.get(appContext)
    private val dao = db.dao()
    private val settings = Settings(appContext)
    private val dir = File(appContext.filesDir, "observations").apply { mkdirs() }

    // ---------- Saving bearings ----------

    /**
     * Saves the photo and the bearing into the active session, starting a new session if
     * there isn't one. Returns the session it went into.
     */
    suspend fun save(observation: Observation, photo: Bitmap): Session = withContext(Dispatchers.IO) {
        ready()
        val session = activeSession() ?: startSession(defaultSessionName(observation.timeMillis))
        val photoFile = File(dir, observation.photoFileName)
        photoFile.outputStream().use { photo.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        try {
            dao.insertObservation(observation.copy(sessionId = session.id, included = true))
        } catch (e: Exception) {
            photoFile.delete()
            throw e
        }
        settings.activeSessionId = session.id
        session
    }

    // ---------- Sessions ----------

    /** The session new bearings go into: the one last chosen, else the most recently used. */
    suspend fun activeSession(): Session? = withContext(Dispatchers.IO) {
        ready()
        settings.activeSessionId?.let { dao.session(it) } ?: dao.mostRecentSession()
    }

    /** Starts a new, empty session, by default making it the active one. */
    suspend fun startSession(name: String, makeActive: Boolean = true): Session = withContext(Dispatchers.IO) {
        ready()
        val session = Session(name = name.trim().ifEmpty { defaultSessionName() }, createdMillis = System.currentTimeMillis())
        val id = dao.insertSession(session)
        if (makeActive) settings.activeSessionId = id
        session.copy(id = id)
    }

    /** New bearings will go into this session. */
    fun makeActive(sessionId: Long) {
        settings.activeSessionId = sessionId
    }

    fun sessionSummaries(): Flow<List<SessionSummary>> = afterReady { dao.sessionSummaries() }

    fun sessions(): Flow<List<Session>> = afterReady { dao.sessionsFlow() }

    fun session(id: Long): Flow<Session?> = afterReady { dao.sessionFlow(id) }

    suspend fun renameSession(id: Long, name: String) = withContext(Dispatchers.IO) {
        if (name.isNotBlank()) dao.renameSession(id, name.trim())
    }

    /** Deletes the session, its bearings and their photos. */
    suspend fun deleteSession(id: Long) = withContext(Dispatchers.IO) {
        val photos = dao.photoFileNames(id)
        dao.deleteSession(id)
        photos.forEach { File(dir, it).delete() }
        if (settings.activeSessionId == id) settings.activeSessionId = null
    }

    // ---------- Observations ----------

    /** A session's bearings, newest first, updating as they change. */
    fun observations(sessionId: Long): Flow<List<Observation>> = afterReady { dao.observationsFlow(sessionId) }

    /** The ticked bearings of a session, oldest first (the order used for A, B, C… labels). */
    suspend fun includedObservations(sessionId: Long): List<Observation> = withContext(Dispatchers.IO) {
        ready()
        dao.observations(sessionId).filter { it.included }.sortedBy { it.timeMillis }
    }

    suspend fun setIncluded(observation: Observation, included: Boolean) = withContext(Dispatchers.IO) {
        dao.setIncluded(observation.id, included)
    }

    suspend fun move(observation: Observation, toSessionId: Long) = withContext(Dispatchers.IO) {
        dao.moveObservation(observation.id, toSessionId)
    }

    suspend fun delete(observation: Observation) = withContext(Dispatchers.IO) {
        dao.deleteObservation(observation.id)
        File(dir, observation.photoFileName).delete()
    }

    /** Small preview image, decoded at reduced size to save memory. */
    suspend fun loadThumbnail(observation: Observation, targetPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(dir, observation.photoFileName)
        if (!file.exists()) return@withContext null
        var sample = 1
        while (maxOf(observation.photoWidth, observation.photoHeight) / (sample * 2) >= targetPx) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    // ---------- Moving bearings saved by version 0.3 (JSON files) into the database ----------

    private fun <T> afterReady(source: () -> Flow<T>): Flow<T> = flow {
        ready()
        emitAll(source())
    }

    private suspend fun ready() {
        migrationLock.withLock {
            if (!migrated) {
                withContext(Dispatchers.IO) { migrateLegacyJson() }
                migrated = true
            }
        }
    }

    /**
     * Before the database, each bearing was a JSON file next to its photo. Moves them into one
     * session called "Earlier bearings". The JSON files are deleted only after the move worked;
     * if it fails, it is tried again next time.
     */
    private suspend fun migrateLegacyJson() {
        dir.listFiles { f -> f.name.endsWith(".json.tmp") }?.forEach { it.delete() }
        val files = dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty()
        if (files.isEmpty()) return
        try {
            val observations = files.mapNotNull { file ->
                runCatching { Observation.fromJson(JSONObject(file.readText())) }
                    .onFailure { Log.w(TAG, "Skipping unreadable ${file.name}", it) }
                    .getOrNull()
            }.filter { dao.countWithId(it.id) == 0 } // already moved by an earlier, interrupted run
            if (observations.isNotEmpty()) {
                db.withTransaction {
                    val sessionId = dao.insertSession(
                        Session(name = LEGACY_SESSION_NAME, createdMillis = observations.minOf { it.timeMillis })
                    )
                    dao.insertObservations(observations.map { it.copy(sessionId = sessionId, included = true) })
                }
            }
            files.forEach { it.delete() }
            Log.i(TAG, "Moved ${observations.size} saved bearings into the database")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't move saved bearings into the database; will retry", e)
        }
    }

    companion object {
        private const val TAG = "ObservationStore"
        private const val JPEG_QUALITY = 85
        private const val LEGACY_SESSION_NAME = "Earlier bearings"

        // Shared by every store instance, so the move from JSON runs once per app start.
        private val migrationLock = Mutex()
        private var migrated = false

        /** E.g. "Session 11 Nov 14:05". */
        fun defaultSessionName(timeMillis: Long = System.currentTimeMillis()): String =
            "Session " + SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(timeMillis))
    }
}
