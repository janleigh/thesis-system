package com.chate.thesis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapsModeLogicTest {

    @Test
    fun testModeCycleOnTap() {
        var mode = CapsMode.OFF
        assertEquals(CapsMode.OFF, mode)
        assertFalse(mode.isShifted())

        mode = mode.nextMode()
        assertEquals(CapsMode.ON_ONCE, mode)
        assertTrue(mode.isShifted())

        mode = mode.nextMode()
        assertEquals(CapsMode.LOCKED, mode)
        assertTrue(mode.isShifted())

        mode = mode.nextMode()
        assertEquals(CapsMode.OFF, mode)
        assertFalse(mode.isShifted())
    }

    @Test
    fun testOnOnceResetsAfterLetterOnly() {
        var mode = CapsMode.ON_ONCE
        // Typing a symbol or space does not reset ON_ONCE
        mode = mode.onCharacterTyped(" ")
        assertEquals(CapsMode.ON_ONCE, mode)

        mode = mode.onCharacterTyped("1")
        assertEquals(CapsMode.ON_ONCE, mode)

        // Typing a letter resets to OFF
        mode = mode.onCharacterTyped("A")
        assertEquals(CapsMode.OFF, mode)
    }

    @Test
    fun testLockedDoesNotResetOnLetter() {
        var mode = CapsMode.LOCKED
        mode = mode.onCharacterTyped("H")
        assertEquals(CapsMode.LOCKED, mode)

        mode = mode.onCharacterTyped("E")
        assertEquals(CapsMode.LOCKED, mode)

        mode = mode.onCharacterTyped("L")
        assertEquals(CapsMode.LOCKED, mode)

        mode = mode.onCharacterTyped("L")
        assertEquals(CapsMode.LOCKED, mode)

        mode = mode.onCharacterTyped("O")
        assertEquals(CapsMode.LOCKED, mode)
    }

    @Test
    fun testShiftKeyVisualAttributes() {
        fun getShiftKeyRepresentation(mode: CapsMode): Pair<String, Boolean> {
            return when (mode) {
                CapsMode.OFF -> Pair("⇧", false)
                CapsMode.ON_ONCE -> Pair("⬆", true)
                CapsMode.LOCKED -> Pair("⇪", true)
            }
        }

        val (iconOff, isAccentOff) = getShiftKeyRepresentation(CapsMode.OFF)
        assertEquals("⇧", iconOff)
        assertFalse(isAccentOff)

        val (iconOnce, isAccentOnce) = getShiftKeyRepresentation(CapsMode.ON_ONCE)
        assertEquals("⬆", iconOnce)
        assertTrue(isAccentOnce)

        val (iconLocked, isAccentLocked) = getShiftKeyRepresentation(CapsMode.LOCKED)
        assertEquals("⇪", iconLocked)
        assertTrue(isAccentLocked)
    }
}
