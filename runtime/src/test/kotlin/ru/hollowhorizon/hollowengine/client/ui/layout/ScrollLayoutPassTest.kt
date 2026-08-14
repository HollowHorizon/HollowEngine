package ru.hollowhorizon.hollowengine.client.ui.layout

import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.scroll.UiScrollHandle
import ru.hollowhorizon.hollowengine.client.ui.scroll.UiScrollState
import ru.hollowhorizon.hollowengine.client.ui.style.UiModifierResolver
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Laying out is the expensive part of a scrolled frame, so the pipeline must not redo it.
 */
class ScrollLayoutPassTest {
    private fun tree(scroll: UiScrollHandle, childHeight: Float): Pair<UiNode, BoxNode> {
        val viewport = BoxNode(
            id = "viewport",
            measurePolicy = UiMeasurePolicies.box(),
            modifiers = listOf(Modifier.size(100.px, 100.px) then scrollModifier(horizontal = false, state = scroll)),
        ).also { it.children.add(BoxNode(id = "content", modifiers = listOf(Modifier.size(80.px, childHeight.px)))) }
        val root = BoxNode(measurePolicy = UiMeasurePolicies.Column).also { it.children.add(viewport) }
        UiModifierResolver().resolve(root)
        return root to viewport
    }

    private fun passes(pipeline: UiLayoutPipeline, root: UiNode, scrollState: UiScrollState): Int {
        val profile = UiProfiler().also { it.enabled = true }.beginFrame()!!
        pipeline.compute(root, 300f, 300f, scrollState, profile)
        return profile.placementPasses
    }

    @Test
    fun `a settled scroll container lays out once per frame`() {
        val scroll = UiScrollHandle()
        val (root, _) = tree(scroll, childHeight = 300f)
        val scrollState = UiScrollState()
        val pipeline = UiLayoutPipeline()

        pipeline.compute(root, 300f, 300f, scrollState)
        pipeline.compute(root, 300f, 300f, scrollState)

        repeat(4) {
            scrollState.setImmediate(scroll, y = scroll.offsetY + 20f)
            assertEquals(1, passes(pipeline, root, scrollState), "scrolling must not re-derive the gutters")
        }
    }

    @Test
    fun `a gutter appearing costs one extra pass and settles again`() {
        val scroll = UiScrollHandle()
        val (root, viewport) = tree(scroll, childHeight = 300f)
        val scrollState = UiScrollState()
        val pipeline = UiLayoutPipeline()

        // First sight of the overflow: the empty guess is wrong once.
        assertTrue(passes(pipeline, root, scrollState) <= 2, "a new gutter costs at most one redo")
        assertEquals(1, passes(pipeline, root, scrollState), "and the guess is right from then on")
        assertTrue(pipeline.compute(root, 300f, 300f, scrollState).scrollbars.containsKey(viewport))
    }
}
