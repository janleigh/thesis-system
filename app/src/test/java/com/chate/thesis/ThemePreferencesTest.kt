package com.chate.thesis

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemePreferencesTest {
    @Test
    fun testDefaultValues() {
        assertEquals("SYSTEM", ThemeMode.SYSTEM.name)
        assertEquals("MONET", ColorPalette.MONET.name)
        assertEquals(ColorPalette.MONET, ColorPalette.fromId("monet"))
        assertEquals(ColorPalette.CYAN, ColorPalette.fromId("cyan"))
        assertEquals(ColorPalette.BLUE, ColorPalette.fromId("blue"))
        assertEquals(ColorPalette.EMERALD, ColorPalette.fromId("emerald"))
        assertEquals(ColorPalette.AMBER, ColorPalette.fromId("amber"))
        assertEquals(ColorPalette.VIOLET, ColorPalette.fromId("violet"))
        assertEquals(ColorPalette.CRIMSON, ColorPalette.fromId("crimson"))
        assertEquals(HeaderEffect.NOTHING, HeaderEffect.fromId("nothing"))
        assertEquals(HeaderEffect.CODE_RAIN, HeaderEffect.fromId("code_rain"))
        assertEquals(HeaderEffect.SNOW, HeaderEffect.fromId("snow"))
        assertEquals(HeaderEffect.MAZE, HeaderEffect.fromId("maze"))
        assertEquals(HeaderEffect.CIRCUIT, HeaderEffect.fromId("circuit"))
    }

    @Test
    fun testColorPaletteLookupFallback() {
        assertEquals(ColorPalette.MONET, ColorPalette.fromId("unknown_value"))
        assertEquals(HeaderEffect.NOTHING, HeaderEffect.fromId("invalid_effect"))
    }
}
