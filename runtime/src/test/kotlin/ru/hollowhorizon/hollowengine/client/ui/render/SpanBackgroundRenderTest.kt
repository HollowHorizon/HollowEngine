package ru.hollowhorizon.hollowengine.client.ui.render

import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.layout.UiLayoutPipeline
import ru.hollowhorizon.hollowengine.client.ui.scroll.UiScrollState
import ru.hollowhorizon.hollowengine.client.ui.style.UiCaretBlinkKeyframes
import ru.hollowhorizon.hollowengine.client.ui.style.UiCaretBlinkPeriodMillis
import ru.hollowhorizon.hollowengine.client.ui.style.UiModifierResolver
import ru.hollowhorizon.hollowengine.client.ui.style.compileHss
import ru.hollowhorizon.hollowengine.client.ui.style.opacity
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Fallback font: 6px/glyph @ fontSize 10, 6px space, line height = fontSize. */
class SpanBackgroundRenderTest {
    private fun render(
        width: Float,
        vararg children: UiNode,
        sheet: String? = null,
        nowMillis: Long = 0L,
    ): List<UiRenderCommand> {
        val container = BoxNode(
            id = "flow",
            measurePolicy = UiMeasurePolicies.InlineFlow,
            modifiers = listOf(Modifier.size(width.px, UiLength.Auto)),
        )
        children.forEach { container.children.add(it) }
        val root = BoxNode(measurePolicy = UiMeasurePolicies.Column).also { it.children.add(container) }
        val resolver = sheet?.let { UiModifierResolver(stylesheet = compileHss(it)) } ?: UiModifierResolver()
        resolver.resolve(root)
        val layout = UiLayoutPipeline().compute(root, 1000f, 1000f, UiScrollState())
        return UiCommandRenderer().collect(root, layout)
    }

    private fun span(text: String, vararg mods: Modifier) = SpanNode(text, modifiers = mods.toList())

    private fun spanBoxes(commands: List<UiRenderCommand>) =
        commands.filterIsInstance<DrawBoxCommand>()
            .filter { it.node is SpanNode && it.phase == UiRenderPhase.BACKGROUND }

    @Test
    fun `span background is one continuous box per line covering inner spaces`() {
        val s = span("aa bb", Modifier.background(UiColor(0f, 1f, 0f, 0.5f)))
        val boxes = spanBoxes(render(400f, s))
        assertEquals(1, boxes.size, "one line => one background box")
        assertEquals(30f, boxes.single().rect.width, 0.6f, "box spans word-to-word incl. the space")
    }

    @Test
    fun `wrapped span paints a background box per line`() {
        val s = span("aaaa bbbb cccc", Modifier.background(UiColor(0f, 1f, 0f, 0.5f)))
        val boxes = spanBoxes(render(60f, s))
        assertEquals(2, boxes.size, "one background box per wrapped line")
        val widths = boxes.map { it.rect.width }.sortedDescending()
        assertEquals(54f, widths[0], 1f)
        assertEquals(24f, widths[1], 1f)
    }

    @Test
    fun `span without background paints no box`() {
        assertTrue(spanBoxes(render(400f, span("aa bb"))).isEmpty())
    }

    @Test
    fun `caret blink animates opacity via the engine keyframes without a special node`() {
        val caret = BoxNode(
            id = "caret",
            modifiers = listOf(
                Modifier.size(2.px, 12.px)
                    .background(UiColor.White)
                    .animation(UiCaretBlinkKeyframes, UiCaretBlinkPeriodMillis, iterationCount = Float.POSITIVE_INFINITY),
            ),
        )
        val root = BoxNode(measurePolicy = UiMeasurePolicies.Column).also { it.children.add(caret) }
        val resolver = UiModifierResolver()

        resolver.resolve(root, nowMillis = 0L)
        assertEquals(1f, caret.resolvedSnapshot.opacity, 0.01f, "fully visible at cycle start")

        resolver.resolve(root, nowMillis = 800L)
        assertEquals(0f, caret.resolvedSnapshot.opacity, 0.01f, "hidden past 56% of the 900ms cycle")
    }
}
