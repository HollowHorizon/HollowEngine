package ru.hollowhorizon.hollowengine.client.ui.widgets

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The field's geometry model (headless font: 6px/glyph, 10px line height). Caret/click/scroll all
 * read from this one measurement, so an offset maps to a boundary and a boundary maps back — even at
 * the bottom of a large document (the caret-jump regression) and through virtualization.
 */
class EditableFieldLayoutTest {
    private val fontSize = 10f
    private fun layout(text: String, wrap: Boolean = false, viewportWidth: Float = 0f) =
        computeEditableFieldLayout(text, fontSize, fontFamily = null, wrap = wrap, viewportWidth = viewportWidth)

    @Test
    fun `non-wrapped lines stack at a uniform height and size to the widest line`() {
        val l = layout("abc\nxy\nlonger")
        assertEquals(0f, l.lineTop(0), 0.5f)
        assertEquals(10f, l.lineTop(1), 0.5f)
        assertEquals(20f, l.lineTop(2), 0.5f)
        assertEquals(36f + fontSize, l.contentWidth, 0.5f, "widest line 'longer' (36px) + trailing margin")
    }

    @Test
    fun `caret offset maps to a glyph boundary on the right line`() {
        val l = layout("abc\nxy\nlonger")
        assertEquals(0f, l.caretAt(0).let { it.x }, 0.5f)
        assertEquals(12f, l.caretAt(2).x, 0.5f, "after 'ab'")
        assertEquals(0f, l.caretAt(4).x, 0.5f) // start of "xy"
        assertEquals(10f, l.caretAt(4).y, 0.5f, "second line")
        assertEquals(20f, l.caretAt(7).y, 0.5f, "third line")
    }

    @Test
    fun `clicking maps back to the caret offset on the clicked row`() {
        val l = layout("abc\nxy\nlonger")
        assertEquals(2, l.offsetAt(12f, 0f), "column 2 on the first line")
        assertEquals(4, l.offsetAt(0f, 12f), "start of the second line")
        // Deep in a large document the bottom row still maps correctly (no jump to an early line).
        val big = layout((0 until 5000).joinToString("\n") { "line$it" })
        assertEquals("line4999", big.lines.last().text)
        val lastTop = big.lineTop(4999)
        assertEquals(big.lines[4999].start, big.offsetAt(0f, lastTop + 1f), "click on the last row")
    }

    @Test
    fun `the visible range is a band around the scroll offset, not the whole document`() {
        val l = layout((0 until 5000).joinToString("\n") { "l$it" })
        val range = l.visibleRange(scrollY = 1000f, viewportHeight = 100f, overscan = 0f)
        assertEquals(100, range.first, "1000 / 10px line")
        assertEquals(110, range.last, "(1000 + 100) / 10px line")
        assertTrue(range.last - range.first < 50, "only a band is composed")
    }

    @Test
    fun `wrapping splits a long line into taller rows`() {
        val l = layout("aaaa bbbb cccc dddd", wrap = true, viewportWidth = 60f)
        assertTrue(l.lineLayouts[0] != null, "line has a wrap layout")
        assertTrue(l.lineTop(1) > fontSize, "the wrapped first line is taller than one row")
    }
}
