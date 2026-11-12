package com.triangulation.locator.data

import android.content.Context
import androidx.core.content.edit

/** Small user preferences, stored on the phone only. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /**
     * Whether to download OpenStreetMap map images. Null means the user hasn't been asked yet.
     * Off until the user turns it on: this is the app's only network use.
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

    private companion object {
        const val KEY_CAR_SAFETY = "car_safety_acknowledged"
        const val KEY_MAP_TILES = "map_tiles_enabled"
        const val KEY_ACTIVE_SESSION = "active_session_id"
    }
}
