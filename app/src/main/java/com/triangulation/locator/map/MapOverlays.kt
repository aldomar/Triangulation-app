package com.triangulation.locator.map

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Typeface
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/** A labelled dot drawn at a fixed screen size, whatever the zoom level. */
data class MapDot(val point: GeoPoint, val color: Int, val label: String?, val radiusDp: Float = 7f)

/**
 * Draws observer positions and the estimated target as simple dots with labels.
 * Lighter than osmdroid's Marker (no info windows, no image resources).
 */
class DotsOverlay(private val dots: List<MapDot>, density: Float) : Overlay() {
    private val scale = density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = android.graphics.Color.WHITE
        strokeWidth = 2.5f * density
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 14f * density
        typeface = Typeface.DEFAULT_BOLD
        color = android.graphics.Color.BLACK
    }
    private val halo = Paint(text).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f * density
        color = android.graphics.Color.WHITE
    }
    private val p = Point()

    override fun draw(c: Canvas, osmv: MapView, shadow: Boolean) {
        if (shadow) return
        val projection = osmv.projection
        for (dot in dots) {
            projection.toPixels(dot.point, p)
            val r = dot.radiusDp * scale
            fill.color = dot.color
            c.drawCircle(p.x.toFloat(), p.y.toFloat(), r, fill)
            c.drawCircle(p.x.toFloat(), p.y.toFloat(), r, ring)
            dot.label?.let {
                val x = p.x + r + 4 * scale
                val y = p.y - r
                c.drawText(it, x, y, halo)
                c.drawText(it, x, y, text)
            }
        }
    }
}
