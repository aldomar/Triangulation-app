package com.triangulation.locator.terrain

import com.triangulation.locator.solver.LatLon
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/** One elevation tile (Web Mercator / "slippy map" numbering). */
data class TileKey(val z: Int, val x: Int, val y: Int)

/**
 * The free "Terrain Tiles" elevation dataset on AWS Open Data (Terrarium encoding).
 * Each tile is a 256×256 PNG; height = (R × 256 + G + B / 256) − 32768 metres.
 * Sources are mainly SRTM (about 30 m), with finer data in some countries.
 */
object Terrarium {
    /** Zoom 12: one pixel is about 38 m at the equator, 27 m at 45° latitude. */
    const val ZOOM = 12
    const val TILE_SIZE = 256
    private const val MAX_LAT = 85.05112878

    fun url(key: TileKey) = "https://elevation-tiles-prod.s3.amazonaws.com/terrarium/${key.z}/${key.x}/${key.y}.png"

    fun elevationFromRgb(r: Int, g: Int, b: Int): Double = r * 256.0 + g + b / 256.0 - 32768.0

    /** Position in pixels across the whole world map at [zoom] (x east, y south). */
    fun globalPixel(p: LatLon, zoom: Int): Pair<Double, Double> {
        val n = TILE_SIZE * (1 shl zoom).toDouble()
        val x = (p.lon + 180.0) / 360.0 * n
        val latR = Math.toRadians(p.lat.coerceIn(-MAX_LAT, MAX_LAT))
        val y = (1.0 - ln(tan(latR) + 1.0 / cos(latR)) / PI) / 2.0 * n
        return x to y
    }
}

/**
 * Looks up heights in Terrarium tiles, smoothing between pixels (bilinear).
 * [tiles] returns a loaded tile's 256×256 heights (row by row), or null if it isn't loaded.
 * The first tile found missing is kept in [missingTile] so the caller can load it and retry.
 */
class TerrariumGrid(
    private val zoom: Int = Terrarium.ZOOM,
    private val tiles: (TileKey) -> FloatArray?,
) : ElevationLookup {
    var missingTile: TileKey? = null
        private set

    override fun elevationM(p: LatLon): Double? {
        val (gx, gy) = Terrarium.globalPixel(p, zoom)
        // Pixel values sit at pixel centres.
        val fx = gx - 0.5
        val fy = gy - 0.5
        val x0 = floor(fx).toInt()
        val y0 = floor(fy).toInt()
        val tx = fx - x0
        val ty = fy - y0
        val a = pixel(x0, y0) ?: return null
        val b = pixel(x0 + 1, y0) ?: return null
        val c = pixel(x0, y0 + 1) ?: return null
        val d = pixel(x0 + 1, y0 + 1) ?: return null
        val top = a + (b - a) * tx
        val bottom = c + (d - c) * tx
        return top + (bottom - top) * ty
    }

    private fun pixel(px: Int, py: Int): Double? {
        val size = Terrarium.TILE_SIZE
        val worldPixels = size shl zoom
        val x = Math.floorMod(px, worldPixels)
        val y = py.coerceIn(0, worldPixels - 1)
        val key = TileKey(zoom, x / size, y / size)
        val data = tiles(key)
        if (data == null) {
            if (missingTile == null) missingTile = key
            return null
        }
        return data[(y % size) * size + (x % size)].toDouble()
    }
}
