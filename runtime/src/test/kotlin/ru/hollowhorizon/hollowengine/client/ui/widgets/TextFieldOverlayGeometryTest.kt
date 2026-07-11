package ru.hollowhorizon.hollowengine.client.ui.widgets

import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.ui.UiTextAlign
import ru.hollowhorizon.hollowengine.client.ui.text.UiTextLayout
import ru.hollowhorizon.hollowengine.client.ui.text.UiTextLayouter
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Caret/selection overlay geometry over the headless font fallback (6px/glyph, 6px space, line
 * height = fontSize). Positions come straight from the laid-out text, so the caret sits on the
 * glyph boundaries without displacing them.
 */
class TextFieldOverlayGeometryTest {
    private val fontSize = 10f

    private fun layout(text: String): UiTextLayout = UiTextLayouter.layout(
        text = text,
        width = 1000f,
        height = Float.POSITIVE_INFINITY,
        wrap = false,
        align = UiTextAlign.LEFT,
        fontSize = fontSize,
        preserveWhitespace = true,
    )

    private fun geometry(text: String, vararg ranges: UiTextCaret) =
        textFieldOverlayGeometry(layout(text), ranges.toList(), fontSize)

    @Test
    fun `caret sits at the glyph boundary for its offset`() {
        val g = geometry("abc", UiTextCaret(0), UiTextCaret(3))
        assertEquals(2, g.carets.size)
        assertEquals(0f, g.carets[0].x, 0.5f)
        assertEquals(18f, g.carets[1].x, 0.5f, "3 glyphs * 6px")
        assertEquals(fontSize, g.carets[0].height, 0.5f)
    }

    @Test
    fun `leading spaces push the caret to the right`() {
        val g = geometry("  a", UiTextCaret(2))
        assertEquals(12f, g.carets[0].x, 0.5f, "two 6px spaces")
    }

    @Test
    fun `a caret with no selection produces no highlight`() {
        val g = geometry("abcdef", UiTextCaret(3))
        assertTrue(g.selections.isEmpty())
    }

    @Test
    fun `a selection highlights the covered glyph run`() {
        val g = geometry("abcdef", UiTextCaret(4, selectionAnchor = 1))
        assertEquals(1, g.selections.size)
        val rect = g.selections.single()
        assertEquals(6f, rect.x, 0.5f, "starts after 'a'")
        assertEquals(18f, rect.width, 0.5f, "covers 'bcd'")
    }

    @Test
    fun `a multi-line selection highlights each line and the caret drops a row`() {
        val g = geometry("ab\ncd", UiTextCaret(5, selectionAnchor = 0))
        assertEquals(2, g.selections.size, "one rect per line")
        assertEquals(fontSize, g.carets[0].y, 0.5f, "caret on the second line")
    }
}
