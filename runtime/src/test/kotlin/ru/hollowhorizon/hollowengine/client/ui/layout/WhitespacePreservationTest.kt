package ru.hollowhorizon.hollowengine.client.ui.layout

import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.scroll.UiScrollState
import ru.hollowhorizon.hollowengine.client.ui.style.UiModifierResolver
import ru.hollowhorizon.hollowengine.client.ui.text.UiTextRun
import kotlin.test.assertEquals

class WhitespacePreservationTest {
    private fun span(text: String, preserve: Boolean): UiLayoutNode {
        val mods = if (preserve) listOf(Modifier.whitespace(UiWhitespace.PRESERVE)) else emptyList()
        val span = SpanNode(text, modifiers = mods)
        val container = BoxNode(
            measurePolicy = UiMeasurePolicies.InlineFlow,
            modifiers = listOf(Modifier.size(1000.px, UiLength.Auto)),
        ).also { it.children.add(span) }
        val root = BoxNode(measurePolicy = UiMeasurePolicies.Column, modifiers = listOf(TestFontStyle)).also { it.children.add(container) }
        UiModifierResolver().resolve(root)
        val layout = UiLayoutPipeline().compute(root, 1000f, 1000f, UiScrollState())
        return layout.nodes.getValue(span)
    }

    private fun UiLayoutNode.wordX(index: Int): Float =
        textLayout!!.lines[0].fragments.filterIsInstance<UiTextRun>()[index].x

    @Test
    fun `collapse folds an inner run of spaces into one`() {
        // "a" (6) + one space (6) => "b" at 12.
        assertEquals(13.333f, span("a  b", preserve = false).wordX(1), 0.5f)
    }

    @Test
    fun `preserve keeps every inner space`() {
        // "a" (6) + two spaces (12) => "b" at 18.
        assertEquals(20f, span("a  b", preserve = true).wordX(1), 0.5f)
    }

    @Test
    fun `collapse drops leading spaces`() {
        assertEquals(0f, span("  a", preserve = false).wordX(0), 0.5f)
    }

    @Test
    fun `preserve keeps leading indentation and the span box covers it`() {
        val node = span("  a", preserve = true)
        assertEquals(13.333f, node.wordX(0), 0.5f, "'a' sits after two 6.667px spaces")
        assertEquals(20f, node.rect.width, 0.5f, "box spans the indent plus the glyph")
    }
}
