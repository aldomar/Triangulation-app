package com.triangulation.locator.data

import android.content.Context
import androidx.core.content.edit
import com.triangulation.locator.terrain.TerrainMath

/** Small user preferences, stored on the phone only. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /**
     * Whether to download OpenStreetMap map images. Null means the user hasn't been asked yet.
     * Off until the user turns it on (the only other network use is elevation data, also opt-in).
     */
    var mapTilesEnabled: Boolean?
        get() = if (prefs.contains(KEY_MAP_TILES)) prefs.getBoolean(KEY_MAP_TILES, false) else null
        set(value) = prefs.edit {
            if (value == null) remove(KEY_MAP_TILES) else putBoolean(KEY_MAP_TILES, value)
        }

    /** The session new bearings go into. Null = use the most recently used session. */
    var activeSessionId: Long?
        get() = if (prefs.contains(KEY_ACTIVE_SESSION)) prefs.getLong(KEY_ACTIVE_SESSION, 0) else null
        set(value) = prefs.edit {
            if (value == null) remove(KEY_ACTIVE_SESSION) else putLong(KEY_ACTIVE_SESSION, value)
        }

    /** The user has read the "passenger only" notice for car mode. */
    var carSafetyAcknowledged: Boolean
        get() = prefs.getBoolean(KEY_CAR_SAFETY, false)
        set(value) = prefs.edit { putBoolean(KEY_CAR_SAFETY, value) }

    /**
     * Whether to download elevation data for terrain (single-photo) estimates. Null means the
     * user hasn't been asked yet. Already-saved elevation data is used either way.
     */
    var elevationDownloadsEnabled: Boolean?
        get() = if (prefs.contains(KEY_ELEVATION)) prefs.getBoolean(KEY_ELEVATION, false) else null
        set(value) = prefs.edit {
            if (value == null) remove(KEY_ELEVATION) else putBoolean(KEY_ELEVATION, value)
        }

    /** The "Terrain estimate" switch on the map. */
    var showTerrain: Boolean
        get() = prefs.getBoolean(KEY_SHOW_TERRAIN, false)
        set(value) = prefs.edit { putBoolean(KEY_SHOW_TERRAIN, value) }

    /** How far off the camera tilt might be, ± degrees, for terrain estimates. */
    var tiltErrorDeg: Double
        get() = prefs.getFloat(KEY_TILT_ERROR, TerrainMath.DEFAULT_TILT_ERROR_DEG.toFloat()).toDouble()
        set(value) = prefs.edit { putFloat(KEY_TILT_ERROR, value.toFloat()) }

    /** The help guide has been shown once on first launch. */
    var helpShown: Boolean
        get() = prefs.getBoolean(KEY_HELP_SHOWN, false)
        set(value) = prefs.edit { putBoolean(KEY_HELP_SHOWN, value) }

    private companion object {
        const val KEY_HELP_SHOWN = "help_shown"
        const val KEY_ELEVATION = "elevation_downloads_enabled"
        const val KEY_SHOW_TERRAIN = "show_terrain"
        const val KEY_TILT_ERROR = "tilt_error_deg"
        const val KEY_CAR_SAFETY = "car_safety_acknowledged"
        const val KEY_MAP_TILES = "map_tiles_enabled"
        const val KEY_ACTIVE_SESSION = "active_session_id"
    }
}
