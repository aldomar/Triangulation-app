package com.triangulation.locator.camera

import com.triangulation.locator.data.BearingMath
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.atan
import kotlin.math.tan

class CameraOpticsTest {
    // A typical main camera: 4000 x 3000 sensor, 1.0 µm pixels, 4.0 mm lens (about 53° wide at 1×).
    private val optics = CameraOptics(
        focalLengthMm = 4.0f,
        pixelSizeMm = 0.001,
        activeArrayLongPx = 4000,
        activeArrayShortPx = 3000,
    )

    @Test
    fun oneTimesMatchesTheSensor() {
        // Landscape photo the size of the sensor: 4 mm / 1 µm = 4000 px focal length, photo 4000 px wide.
        assertEquals(1.0, optics.focalFraction(4000, 3000), 1e-6)
        // Portrait 1440 x 1920 (scaled to 0.48): focal 1920 px over 1440 px width.
        assertEquals(1920.0 / 1440.0, optics.focalFraction(1440, 1920), 1e-6)
    }

    @Test
    fun zoomMultipliesTheFocalLength() {
        val base = optics.focalFraction(1440, 1920)
        listOf(3.0, 5.0, 10.0).forEach { z ->
            assertEquals(base * z, optics.focalFraction(1440, 1920, z), 1e-9)
        }
    }

    @Test
    fun zoomShrinksTheTapAngleForTheSamePixel() {
        // Tap at the right edge of the photo, level phone: angle = half the field of view.
        val oneX = BearingMath.tapOffsetDeg(1.0, 0.5, optics.focalFraction(1440, 1920), 4.0 / 3.0, 0.0)
        val fiveX = BearingMath.tapOffsetDeg(1.0, 0.5, optics.focalFraction(1440, 1920, 5.0), 4.0 / 3.0, 0.0)
        // Half-width angle at 5× is atan(tan(halfFov at 1×) / 5), close to (but not exactly) a fifth.
        val expected = Math.toDegrees(atan(tan(Math.toRadians(oneX)) / 5.0))
        assertEquals(expected, fiveX, 1e-9)
        assertEquals(oneX / 5.0, fiveX, 0.2)
    }

    @Test
    fun fallbackAlsoScalesWithZoom() {
        assertEquals(BearingMath.fallbackFocalFraction() * 5.0, BearingMath.fallbackFocalFraction(5.0), 1e-12)
    }
}
