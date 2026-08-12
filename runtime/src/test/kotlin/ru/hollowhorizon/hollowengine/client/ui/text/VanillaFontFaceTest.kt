package ru.hollowhorizon.hollowengine.client.ui.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VanillaFontFaceTest {
    private val face = assertNotNull(
        UiVanillaFont.face("vanilla"),
        "the vanilla font must be readable from the classpath",
    )

    @Test
    fun `a space is four pixels, from the space provider rather than the blank ascii cell`() {
        assertEquals(4f / UiVanillaFont.EmPixels, face.advance(' '))
    }

    @Test
    fun `ascii advances match vanilla's own widths`() {
        assertEquals(6f / UiVanillaFont.EmPixels, face.advance('a'))
        assertEquals(2f / UiVanillaFont.EmPixels, face.advance('i'))
        assertEquals(6f / UiVanillaFont.EmPixels, face.advance('A'))
    }

    @Test
    fun `a line is nine pixels tall, as in vanilla`() {
        assertEquals(9f, face.lineHeight(UiVanillaFont.EmPixels))
    }

    @Test
    fun `width of a string is the sum of its advances`() {
        val text = "abc"
        val expected = text.sumOf { face.advance(it).toDouble() }.toFloat() * UiVanillaFont.EmPixels
        assertEquals(expected, face.width(text, UiVanillaFont.EmPixels))
    }

    @Test
    fun `cyrillic comes from the non-latin sheet, not the fallback glyph`() {
        assertTrue(face.advance('Ж') > 0f)
        assertTrue(face.advance('щ') > 0f)
    }

    @Test
    fun `an uncovered codepoint still advances, so text never collapses`() {
        assertTrue(face.advance('漢') > 0f, "an unknown glyph falls back rather than measuring zero")
    }

    @Test
    fun `a font that does not exist resolves to nothing rather than throwing`() {
        assertEquals(null, UiVanillaFont.face("vanilla:does_not_exist"))
    }
}
