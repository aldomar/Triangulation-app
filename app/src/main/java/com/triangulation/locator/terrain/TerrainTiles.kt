package com.triangulation.locator.terrain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.triangulation.locator.data.BearingMath
import com.triangulation.locator.data.Observation
import com.triangulation.locator.solver.LatLon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Elevation tiles: kept in memory while in use, saved in the app's private cache folder
 * (capped at about 100 MB; Android may clear it if space runs low), and downloaded only
 * when the user has allowed it. Requests carry the app name/version only.
 */
class TerrainTiles private constructor(context: Context) {
    private val dir = File(context.cacheDir, "terrain")
    private val userAgent: String = run {
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "dev"
        "TriangulationLocator/$version"
    }
    private val memory = object : LinkedHashMap<TileKey, FloatArray>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<TileKey, FloatArray>?) = size > MEMORY_TILES
    }

    enum class Load { Loaded, NotCached, Failed }

    @Synchronized
    fun inMemory(key: TileKey): FloatArray? = memory[key]

    /** Frees the decoded tiles (about 256 KB each). Saved files are kept. */
    @Synchronized
    fun clearMemory() = memory.clear()

    /** Loads a tile from the saved files, or downloads it if [allowDownload]. Blocking: call on an IO thread. */
    fun load(key: TileKey, allowDownload: Boolean): Load {
        val file = File(dir, "${key.z}/${key.x}/${key.y}.png")
        if (file.exists()) {
            decode(file.readBytes())?.let { heights ->
                file.setLastModified(System.currentTimeMillis())
                remember(key, heights)
                return Load.Loaded
            }
            file.delete() // Damaged: fetch it again below.
        }
        if (!allowDownload) return Load.NotCached
        return try {
            val bytes = download(key) ?: return Load.Failed
            val heights = decode(bytes) ?: return Load.Failed
            save(file, bytes)
            remember(key, heights)
            Load.Loaded
        } catch (e: Exception) {
            Log.w(TAG, "Elevation tile $key failed: ${e.message}")
            Load.Failed
        }
    }

    @Synchronized
    private fun remember(key: TileKey, heights: FloatArray) {
        memory[key] = heights
    }

    private fun download(key: TileKey): ByteArray? {
        val conn = URL(Terrarium.url(key)).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", userAgent)
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "Elevation tile $key: HTTP ${conn.responseCode}")
                return null
            }
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    private fun decode(bytes: ByteArray): FloatArray? {
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inPremultiplied = false
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        try {
            val size = Terrarium.TILE_SIZE
            if (bitmap.width != size || bitmap.height != size) return null
            val pixels = IntArray(size * size)
            bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
            return FloatArray(pixels.size) { i ->
                val c = pixels[i]
                Terrarium.elevationFromRgb((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF).toFloat()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun save(file: File, bytes: ByteArray) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(file)) tmp.delete()
        trimCache()
    }

    /** Keeps the saved tiles under the cap by deleting the least recently used ones. */
    private fun trimCache() {
        val files = dir.walkTopDown().filter { it.isFile }.toList()
        var total = files.sumOf { it.length() }
        if (total <= MAX_CACHE_BYTES) return
        for (f in files.sortedBy { it.lastModified() }) {
            if (total <= TRIM_TO_BYTES) break
            total -= f.length()
            f.delete()
        }
    }

    companion object {
        private const val TAG = "TerrainTiles"
        private const val MEMORY_TILES = 24
        private const val MAX_CACHE_BYTES = 100L * 1024 * 1024
        private const val TRIM_TO_BYTES = 80L * 1024 * 1024

        @Volatile private var instance: TerrainTiles? = null

        fun get(context: Context): TerrainTiles = instance ?: synchronized(this) {
            instance ?: TerrainTiles(context.applicationContext).also { instance = it }
        }
    }
}

/** Works out single-photo (terrain) estimates for saved bearings. */
object TerrainEstimator {
    /** Upper limit on tile loads for one bearing (a 40 km line needs around 10–20). */
    private const val MAX_TILE_LOADS = 60

    suspend fun estimate(
        context: Context,
        obs: Observation,
        tiltErrorDeg: Double,
        allowDownload: Boolean,
    ): TerrainResult = withContext(Dispatchers.IO) {
        if (obs.typedIn) return@withContext TerrainResult.NoTilt
        val tiles = TerrainTiles.get(context)
        val angle = BearingMath.elevationAngleDeg(obs)
        repeat(MAX_TILE_LOADS) {
            // The maths runs on tiles already in memory; each missing tile is loaded, then it retries.
            val grid = TerrariumGrid { tiles.inMemory(it) }
            val result = TerrainMath.estimate(
                observer = LatLon(obs.latitude, obs.longitude),
                bearingDeg = obs.bearingDeg,
                bearingErrorDeg = obs.errorDeg,
                angleDeg = angle,
                tiltErrorDeg = tiltErrorDeg,
                lookup = grid,
            )
            val missing = grid.missingTile
            if (result !is TerrainResult.NoData || missing == null) return@withContext result
            when (tiles.load(missing, allowDownload)) {
                TerrainTiles.Load.Loaded -> Unit
                TerrainTiles.Load.NotCached -> return@withContext TerrainResult.NoData(downloadsOff = true)
                TerrainTiles.Load.Failed -> return@withContext TerrainResult.NoData(downloadsOff = false)
            }
        }
        TerrainResult.NoData(downloadsOff = false)
    }
}
