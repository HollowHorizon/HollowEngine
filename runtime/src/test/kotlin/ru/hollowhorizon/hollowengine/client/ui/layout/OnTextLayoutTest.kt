package ru.hollowhorizon.hollowengine.client.ui.layout

import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.text.UiTextLayout
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * `onTextLayout` hands a span its laid-out text after each pass (change-guarded), the hook a
 * composable text field uses to place the caret over the glyphs and map clicks to offsets.
 */
class OnTextLayoutTest {
    private fun frameOf(span: SpanNode): HollowUiRuntime {
        val container = BoxNode(
            measurePolicy = UiMeasurePolicies.InlineFlow,
            modifiers = listOf(Modifier.size(1000.px, UiLength.Auto)),
        ).also { it.children.add(span) }
        val root = BoxNode(measurePolicy = UiMeasurePolicies.Column).also { it.children.add(container) }
        return HollowUiRuntime().also { it.frame(root, 1000f, 1000f, -1f, -1f, 0L) }
    }

    @Test
    fun `onTextLayout reports the span's laid-out lines`() {
        var reported: UiTextLayout? = null
        frameOf(SpanNode("ab\ncd", modifiers = listOf(Modifier.onTextLayout { reported = it })))
        assertNotNull(reported, "callback fired after layout")
        assertEquals(2, reported.lines.size, "two hard-broken lines")
    }

    @Test
    fun `onTextLayout fires again only when the layout changes`() {
        var fires = 0
        val span = SpanNode("hello", modifiers = listOf(Modifier.onTextLayout { fires++ }))
        val container = BoxNode(
            measurePolicy = UiMeasurePolicies.InlineFlow,
            modifiers = listOf(Modifier.size(1000.px, UiLength.Auto)),
        ).also { it.children.add(span) }
        val root = BoxNode(measurePolicy = UiMeasurePolicies.Column).also { it.children.add(container) }
        val runtime = HollowUiRuntime()
        runtime.frame(root, 1000f, 1000f, -1f, -1f, 0L)
        runtime.frame(root, 1000f, 1000f, -1f, -1f, 0L)
        assertEquals(1, fires, "an unchanged layout does not re-fire onTextLayout")
    }
}
