package com.triangulation.locator.map

import android.content.Context
import org.osmdroid.config.Configuration
import java.io.File

private var configured = false

/**
 * One-time map library setup. Must run before the first MapView is created.
 *
 * - Tiles are cached in the app's private cache folder (Android can clear it if space runs low).
 * - The cache is capped at about 50 MB.
 * - The User-Agent names the app only: OSM's tile policy requires an identifying app name,
 *   and nothing personal is included.
 */
fun configureMaps(context: Context) {
    if (configured) return
    val appContext = context.applicationContext
    val version = runCatching {
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
    }.getOrNull() ?: "dev"
    val base = File(appContext.cacheDir, "osmdroid")
    Configuration.getInstance().apply {
        userAgentValue = "TriangulationLocator/$version"
        osmdroidBasePath = base
        osmdroidTileCache = File(base, "tiles")
        tileFileSystemCacheMaxBytes = 50L * 1024 * 1024
        tileFileSystemCacheTrimBytes = 40L * 1024 * 1024
    }
    configured = true
}
