package ru.hollowhorizon.hollowengine.client.ui.text

import kotlin.test.Test
import kotlin.test.assertEquals

class UiFontSourceTest {
    @Test
    fun `a family that names a ttf file is a TrueType font`() {
        assertEquals(UiFontSource.TTF, UiFontSource.of("hollowengine:fonts/onest.ttf"))
        assertEquals(UiFontSource.TTF, UiFontSource.of("file:fonts/Custom.TTF"))
    }

    @Test
    fun `the options after the question mark do not hide the suffix`() {
        assertEquals(UiFontSource.TTF, UiFontSource.of("hollowengine:fonts/onest.ttf?size=48&charset=latin+cyrillic"))
    }

    @Test
    fun `vanilla and atlas families are not read as TrueType`() {
        assertEquals(UiFontSource.VANILLA, UiFontSource.of("vanilla"))
        assertEquals(UiFontSource.VANILLA, UiFontSource.of("vanilla:minecraft:alt"))
        assertEquals(UiFontSource.MSDF_ASSET, UiFontSource.of("hollowengine:fonts/monocraft"))
    }
}
