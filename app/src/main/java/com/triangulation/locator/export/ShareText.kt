package com.triangulation.locator.export

import com.triangulation.locator.data.Observation
import com.triangulation.locator.solver.LatLon
import com.triangulation.locator.solver.SolveResult
import com.triangulation.locator.solver.SolveWarning
import com.triangulation.locator.terrain.TerrainResult
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * The short text sent when the user taps "Share" on the map: the estimated location, how sure
 * it is, when the bearings were taken, and an OpenStreetMap link.
 *
 * Observer positions are deliberately left out. Pure Kotlin, so it unit-tests on the JVM.
 */
object ShareText {
    /**
     * Returns null when there is nothing to share (no crossing point and no terrain estimate
     * for a single bearing).
     */
    fun build(
        sessionName: String,
        observations: List<Observation>,
        result: SolveResult,
        terrain: List<TerrainResult>?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String? {
        val lastMillis = observations.maxOfOrNull { it.timeMillis } ?: return null
        val time = TIME_FORMAT.withZone(zone).format(Instant.ofEpochMilli(lastMillis))
        val singleTerrain = if (observations.size == 1) terrain?.firstOrNull() as? TerrainResult.Found else null

        return when {
            result is SolveResult.Fix -> {
                val p = result.point
                val sure = result.uncertaintyRadiusM?.let { "within about ${distanceText(it)}" }
                    ?: "uncertainty too large to measure"
                val count = observations.size
                buildString {
                    append("$sessionName: estimated location ${coords(p)}, $sure. ")
                    append("From $count bearings, last at $time. ")
                    cautionText(result.warnings)?.let { append("$it ") }
                    append(osmLink(p, result.uncertaintyRadiusM))
                    append(" ")
                    append(DISCLAIMER)
                }
            }
            singleTerrain != null -> {
                val p = singleTerrain.point
                val far = singleTerrain.farM?.let(::distanceText) ?: "beyond ${distanceText(singleTerrain.maxRangeM)}"
                buildString {
                    append("$sessionName: estimated location ${coords(p)}, ")
                    append("about ${distanceText(singleTerrain.distanceM)} from the observer ")
                    append("(likely ${distanceText(singleTerrain.nearM)} to $far). ")
                    append("From 1 bearing and the shape of the land, at $time. ")
                    if (singleTerrain.veryUncertain) append("Treat with caution: the distance is very uncertain. ")
                    append(osmLink(p, (singleTerrain.distanceM - singleTerrain.nearM).coerceAtLeast(200.0)))
                    append(" ")
                    append(DISCLAIMER)
                }
            }
            else -> null
        }
    }

    /** Link that opens openstreetmap.org with a marker at [p], zoomed to suit [radiusM]. */
    fun osmLink(p: LatLon, radiusM: Double?): String {
        val lat = num(p.lat)
        val lon = num(p.lon)
        return "https://www.openstreetmap.org/?mlat=$lat&mlon=$lon#map=${zoomFor(p.lat, radiusM)}/$lat/$lon"
    }

    /** Web-map zoom level that fits about four times [radiusM] across a phone screen. */
    internal fun zoomFor(lat: Double, radiusM: Double?): Int {
        val r = radiusM ?: return 12
        // At zoom z, one 256-px tile covers 40 075 km × cos(lat) / 2^z. Fit 4r into ~400 px.
        val metresAcross = 4 * r.coerceAtLeast(50.0)
        val z = ln(156_543.0 * cos(Math.toRadians(lat)) * 400 / metresAcross) / ln(2.0)
        return floor(z).toInt().coerceIn(5, 17)
    }

    private fun cautionText(warnings: Set<SolveWarning>): String? {
        val reasons = warnings.mapNotNull {
            when (it) {
                SolveWarning.ShallowAngle -> "the sightlines cross at a shallow angle"
                SolveWarning.UncertaintyUnbounded -> "the object could be much further away"
                SolveWarning.BearingsDisagree -> "the bearings disagree"
                SolveWarning.Far -> null
            }
        }
        return if (reasons.isEmpty()) null else "Treat with caution: ${reasons.joinToString(", ")}."
    }

    /** 5 decimals: about 1 m, already finer than any estimate. */
    private fun num(v: Double) = String.format(Locale.US, "%.5f", v)

    private fun coords(p: LatLon) = "${num(p.lat)}, ${num(p.lon)}"

    /** Same wording as the map screen (`ui.distanceText`), kept here so this stays Android-free. */
    private fun distanceText(m: Double): String =
        if (m < 1000) "${(m / 10).roundToInt() * 10} m" else String.format(Locale.US, "%.1f km", m / 1000)

    private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm z, d MMM yyyy", Locale.ENGLISH)

    private const val DISCLAIMER = "An estimate to guide where to look, not an exact location."
}
