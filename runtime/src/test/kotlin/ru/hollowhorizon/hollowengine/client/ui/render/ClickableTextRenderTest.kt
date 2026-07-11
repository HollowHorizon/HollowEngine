package ru.hollowhorizon.hollowengine.client.ui.render

import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.ui.*
import kotlin.test.assertTrue

/**
 * Reproduction: a `Text` (inline-flow container + span) with an `onClick` modifier must still lay
 * out and render its glyphs, exactly like a plain `Text`.
 */
class ClickableTextRenderTest {
    private fun textNode(value: String, vararg mods: Modifier): BoxNode {
        val span = SpanNode(value)
        return BoxNode(measurePolicy = UiMeasurePolicies.InlineFlow, modifiers = mods.toList())
            .also { it.children.add(span) }
    }

    @Test
    fun `a clickable Text lays out and renders like a plain one`() {
        val plain = textNode("plain")
        val clickable = textNode("clicky", Modifier.onClick { })
        val root = BoxNode(measurePolicy = UiMeasurePolicies.Column)
            .also { it.children.add(plain); it.children.add(clickable) }

        val frame = HollowUiRuntime().frame(root, 300f, 300f, -1f, -1f, 0L)
        val plainSpan = plain.children.first()
        val clickableSpan = clickable.children.first()

        assertTrue(frame.layout.nodes.containsKey(clickableSpan), "clickable span is laid out")
        assertTrue(frame.layout.nodes.getValue(clickableSpan).rect.width > 0f, "clickable span has width")
        assertTrue(frame.layout.nodes.getValue(plainSpan).rect.width > 0f, "plain span has width")

        val commands = UiCommandRenderer().collect(frame.root, frame.layout)
        val texts = commands.filterIsInstance<DrawTextCommand>().map { it.text }
        assertTrue(texts.any { it.contains("clicky") }, "clickable text is drawn; got $texts")
        assertTrue(texts.any { it.contains("plain") }, "plain text is drawn; got $texts")
    }
}
