package com.triangulation.locator.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Saves observations in the app's private storage: one JPEG and one small JSON file each.
 * Other apps and the Gallery can't see these files, and uninstalling the app removes them.
 *
 * Phase 4 replaces the JSON files with a Room database.
 */
class ObservationStore(context: Context) {
    private val dir = File(context.filesDir, "observations").apply { mkdirs() }

    suspend fun save(observation: Observation, photo: Bitmap) = withContext(Dispatchers.IO) {
        val photoFile = File(dir, observation.photoFileName)
        photoFile.outputStream().use { photo.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        // Write metadata last, so a half-saved observation never shows up in the list.
        val tmp = File(dir, "${observation.id}.json.tmp")
        tmp.writeText(observation.toJson().toString(2))
        tmp.renameTo(File(dir, "${observation.id}.json"))
    }

    suspend fun list(): List<Observation> = withContext(Dispatchers.IO) {
        dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty()
            .mapNotNull { file ->
                runCatching { Observation.fromJson(JSONObject(file.readText())) }
                    .onFailure { Log.w(TAG, "Skipping unreadable ${file.name}", it) }
                    .getOrNull()
            }
            .sortedByDescending { it.timeMillis }
    }

    suspend fun delete(observation: Observation) = withContext(Dispatchers.IO) {
        File(dir, "${observation.id}.json").delete()
        File(dir, observation.photoFileName).delete()
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        dir.listFiles()?.forEach { it.delete() }
    }

    /** Small preview image, decoded at reduced size to save memory. */
    suspend fun loadThumbnail(observation: Observation, targetPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(dir, observation.photoFileName)
        if (!file.exists()) return@withContext null
        var sample = 1
        while (maxOf(observation.photoWidth, observation.photoHeight) / (sample * 2) >= targetPx) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private companion object {
        const val TAG = "ObservationStore"
        const val JPEG_QUALITY = 85
    }
}
