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
    fun testCircuitChamferPoints() {
        // Authentic PCB routing: start (x1, y1), bend at 45 deg, end (x2, y2)
        val x1 = 10f
        val y1 = 20f
        val x2 = 100f
        val y2 = 60f
        val dy = y2 - y1 // 40
        val bendX = x1 + 30f
        val bendEndX = bendX + kotlin.math.abs(dy) // 45 degree angle dx == dy
        assertEquals(40f, bendEndX - bendX, 0.001f)
        assertTrue(bendEndX <= x2)
    }

    @Test
    fun testPacManMouthOscillation() {
        // Mouth opens from 0 to maxAngle (e.g. 45 degrees)
        fun calculateMouthAngle(frame: Int, maxAngle: Float): Float {
            val progress = kotlin.math.abs(kotlin.math.sin(frame * 0.25)).toFloat()
            return progress * maxAngle
        }
        val angle0 = calculateMouthAngle(0, 45f)
        val anglePeak = calculateMouthAngle(6, 45f) // sin(1.5) approx 0.997
        assertEquals(0f, angle0, 0.001f)
        assertTrue(anglePeak > 40f && anglePeak <= 45f)
    }

    @Test
    fun testPacManScreenWrap() {
        var x = 405f
        val screenWidth = 400f
        val margin = 20f
        if (x > screenWidth + margin) {
            x = -margin
        }
        assertEquals(405f, x, 0.001f)

        x += 20f // 425f > 420f
        if (x > screenWidth + margin) {
            x = -margin
        }
        assertEquals(-20f, x, 0.001f)
    }

    @Test
    fun testPacManPelletConsumption() {
        var eatenCount = 0
        val pacX = 50f
        val pellets = mutableListOf(20f, 48f, 52f, 100f)
        val eatRadius = 8f
        val iterator = pellets.iterator()
        while (iterator.hasNext()) {
            val px = iterator.next()
            if (kotlin.math.abs(px - pacX) <= eatRadius) {
                iterator.remove()
                eatenCount++
            }
        }
        assertEquals(2, eatenCount)
        assertEquals(2, pellets.size)
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

