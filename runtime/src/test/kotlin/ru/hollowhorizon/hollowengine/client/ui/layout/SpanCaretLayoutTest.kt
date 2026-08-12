package ru.hollowhorizon.hollowengine.client.ui.layout

import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.scroll.UiScrollState
import ru.hollowhorizon.hollowengine.client.ui.style.UiModifierResolver
import ru.hollowhorizon.hollowengine.client.ui.style.fontSize
import ru.hollowhorizon.hollowengine.client.ui.text.UiTextLayout
import ru.hollowhorizon.hollowengine.client.ui.text.caretIndexAt
import ru.hollowhorizon.hollowengine.client.ui.text.caretPosition
import kotlin.test.assertEquals

/**
 * A preserved-whitespace span's own layout is caret-exact: offsets land on real glyph boundaries
 * (spaces included) and clicks map back to the same offsets. This is what lets a span-based field
 * edit against the text it actually rendered.
 */
class SpanCaretLayoutTest {

    private fun spanLayout(text: String, width: Float = 1000f): Pair<UiTextLayout, Float> {
        val span = SpanNode(text, modifiers = listOf(Modifier.whitespace(UiWhitespace.PRESERVE)))
        val container = BoxNode(
            measurePolicy = UiMeasurePolicies.InlineFlow,
            modifiers = listOf(Modifier.size(width.px, UiLength.Auto)),
        ).also { it.children.add(span) }
        val root = BoxNode(measurePolicy = UiMeasurePolicies.Column, modifiers = listOf(TestFontStyle)).also { it.children.add(container) }
        UiModifierResolver().resolve(root)
        val layout = UiLayoutPipeline().compute(root, 1000f, 1000f, UiScrollState())
        return layout.nodes.getValue(span).textLayout!! to span.resolvedSnapshot.fontSize
    }

    @Test
    fun `caret offsets fall on glyph boundaries through preserved spaces`() {
        val (layout, size) = spanLayout("a  b")
        assertEquals(0f, layout.caretPosition(0, size).x, 0.5f)
        assertEquals(6.667f, layout.caretPosition(1, size).x, 0.5f, "after 'a'")
        assertEquals(20f, layout.caretPosition(3, size).x, 0.5f, "after 'a' + two spaces")
        assertEquals(26.667f, layout.caretPosition(4, size).x, 0.5f, "after 'b'")
    }

    @Test
    fun `clicking maps back to the caret offset`() {
        val (layout, size) = spanLayout("a  b")
        assertEquals(0, layout.caretIndexAt(0f, 0f, size))
        assertEquals(3, layout.caretIndexAt(18f, 0f, size), "boundary before 'b'")
        assertEquals(4, layout.caretIndexAt(24f, 0f, size))
    }

    @Test
    fun `a second line reports its own source offset`() {
        val (layout, size) = spanLayout("ab\ncd")
        assertEquals(2, layout.lines.size)
        // Caret at 3 is the start of the second line (just after the newline at index 2).
        val second = layout.caretPosition(3, size)
        assertEquals(layout.lines[1].y, second.y, 0.5f)
        assertEquals(0f, second.x, 0.5f)
    }
}
