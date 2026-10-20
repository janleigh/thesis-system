package com.chate.thesis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeaderSurfaceLogicTest {
    @Test
    fun testSnowflakeUpdateWrapsAround() {
        var y = 100f
        val height = 100f
        val speed = 5f
        y += speed
        if (y > height) {
            y = 0f
        }
        assertEquals(0f, y, 0.001f)
    }

    @Test
    fun testCodeRainGlyphRandomization() {
        val chars = "010101XYZABC"
        val sample = chars.random()
        assertTrue(chars.contains(sample))
    }

    @Test
    fun testCircuitPulseWrap() {
        var progress = 0.95f
        val speed = 0.1f
        progress += speed
        if (progress > 1f) {
            progress -= 1f
        }
        assertEquals(0.05f, progress, 0.001f)
    }

    @Test
    fun testMazeScanlineWrap() {
        var scanY = 195f
        val maxY = 200f
        val step = 10f
        scanY += step
        if (scanY > maxY) {
            scanY = 0f
        }
        assertEquals(0f, scanY, 0.001f)
    }
}

