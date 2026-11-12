package com.triangulation.locator.sensors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MagneticCheckTest {
    @Test
    fun ratioNeedsBothValues() {
        assertEquals(1.2, MagneticCheck.ratio(60.0, 50.0)!!, 1e-9)
        assertNull(MagneticCheck.ratio(null, 50.0))
        assertNull(MagneticCheck.ratio(60.0, null))
        assertNull(MagneticCheck.ratio(60.0, 0.0))
        assertNull(MagneticCheck.ratio(0.0, 50.0))
    }

    @Test
    fun disturbedBeyondFifteenPercentEitherWay() {
        assertFalse(MagneticCheck.isDisturbed(null))
        assertFalse(MagneticCheck.isDisturbed(1.0))
        assertFalse(MagneticCheck.isDisturbed(1.14))
        assertFalse(MagneticCheck.isDisturbed(0.86))
        assertTrue(MagneticCheck.isDisturbed(1.16))
        assertTrue(MagneticCheck.isDisturbed(0.84))
    }

    @Test
    fun describesDirectionAndSize() {
        assertEquals("32% too strong", MagneticCheck.describe(1.32))
        assertEquals("20% too weak", MagneticCheck.describe(0.8))
    }
}
