package ru.hollowhorizon.hollowengine.client.ui.layout

import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.scroll.UiScrollHandle
import ru.hollowhorizon.hollowengine.client.ui.scroll.UiScrollState
import ru.hollowhorizon.hollowengine.client.ui.style.UiModifierResolver
import kotlin.test.assertEquals

/**
 * Chrome inside a scroll container (a line-number gutter, a diagnostic overlay) is positioned by
 * the offset so it stays put. Counted as content, it drags the measured extent along with the
 * offset, so how far the view may scroll would depend on where it is already scrolled to.
 */
class ScrollPinnedContentTest {
    private fun rangeAt(offset: Float, pinned: Boolean): Float {
        val scroll = UiScrollHandle()
        val chrome = BoxNode(
            id = "chrome",
            modifiers = listOf(
                Modifier.position(0.px, offset.px).size(20.px, 140.px)
                    .let { if (pinned) it.pinnedToViewport() else it },
            ),
        )
        val viewport = BoxNode(
            id = "viewport",
            measurePolicy = UiMeasurePolicies.box(),
            modifiers = listOf(Modifier.size(100.px, 100.px) then scrollModifier(horizontal = false, state = scroll)),
        )
        viewport.children.add(BoxNode(modifiers = listOf(Modifier.size(80.px, 150.px))))
        viewport.children.add(chrome)
        val root = BoxNode(measurePolicy = UiMeasurePolicies.Column).also { it.children.add(viewport) }
        UiModifierResolver().resolve(root)

        val scrollState = UiScrollState()
        val pipeline = UiLayoutPipeline()
        pipeline.compute(root, 300f, 300f, scrollState)
        scrollState.setImmediate(scroll, y = offset)
        return pipeline.compute(root, 300f, 300f, scrollState).nodes.getValue(viewport).scrollRange.y
    }

    @Test
    fun `pinned chrome leaves the range where the content puts it, at any offset`() {
        // 150px of content in a 100px viewport: 50px of travel, wherever the view happens to be.
        assertEquals(50f, rangeAt(offset = 0f, pinned = true), 0.01f)
        assertEquals(50f, rangeAt(offset = 50f, pinned = true), 0.01f)
    }

    @Test
    fun `the same child counted as content makes the range follow the offset`() {
        assertEquals(50f, rangeAt(offset = 0f, pinned = false), 0.01f)
        assertEquals(90f, rangeAt(offset = 50f, pinned = false), 0.01f)
    }
}
