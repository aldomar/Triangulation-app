package com.triangulation.locator.solver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CoordinateParserTest {
    private fun check(text: String, lat: Double, lon: Double) {
        val p = CoordinateParser.parse(text)
        assertNotNull(text, p)
        assertEquals(text, lat, p!!.lat, 1e-6)
        assertEquals(text, lon, p.lon, 1e-6)
    }

    @Test
    fun decimalDegrees() {
        check("49.2827, -123.1207", 49.2827, -123.1207)
        check("  49.2827 -123.1207 ", 49.2827, -123.1207)
        check("-33.8688,151.2093", -33.8688, 151.2093)
    }

    @Test
    fun hemisphereLetters() {
        check("49.2827 N 123.1207 W", 49.2827, -123.1207)
        check("49.2827N, 123.1207W", 49.2827, -123.1207)
        check("N49.2827 W123.1207", 49.2827, -123.1207)
        check("s 33.8688 e 151.2093", -33.8688, 151.2093)
        // Longitude first, but the letters say which is which.
        check("123.1207 W 49.2827 N", 49.2827, -123.1207)
    }

    @Test
    fun degreesMinutesSeconds() {
        check("49°16'58\"N 123°7'15\"W", 49 + 16 / 60.0 + 58 / 3600.0, -(123 + 7 / 60.0 + 15 / 3600.0))
        check("49°15.5' N, 123°07.5' W", 49 + 15.5 / 60, -(123 + 7.5 / 60))
        check("49º 16′ 58″ N 123º 7′ 15″ W", 49 + 16 / 60.0 + 58 / 3600.0, -(123 + 7 / 60.0 + 15 / 3600.0))
        check("-49°30', 20°", -49.5, 20.0)
    }

    @Test
    fun rejectsNonsense() {
        assertNull(CoordinateParser.parse(""))
        assertNull(CoordinateParser.parse("49.28"))
        assertNull(CoordinateParser.parse("49.28, -123.12, 5"))
        assertNull(CoordinateParser.parse("hello"))
        assertNull(CoordinateParser.parse("49.28 abc -123.12"))
        assertNull(CoordinateParser.parse("95, 10")) // latitude out of range
        assertNull(CoordinateParser.parse("10, 190")) // longitude out of range
        assertNull(CoordinateParser.parse("49.28 N 50.1 S")) // two latitudes
        assertNull(CoordinateParser.parse("-49.28 N 123.1 W")) // minus and N disagree
        assertNull(CoordinateParser.parse("49°75' N 123° W")) // 75 minutes
    }
}
