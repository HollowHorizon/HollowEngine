package ru.hollowhorizon.hollowengine.client.ui.input

import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.layout.UiLayoutPipeline
import ru.hollowhorizon.hollowengine.client.ui.scroll.UiScrollState
import ru.hollowhorizon.hollowengine.client.ui.style.UiModifierResolver
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** hitsVisible decides whether a press is blocked by the UI or falls through to the screen/game. */
class InputPassthroughTest {
    private fun frameOf(root: BoxNode): HollowUiFrame {
        UiModifierResolver().resolve(root)
        val layout = UiLayoutPipeline().compute(root, 400f, 400f, UiScrollState())
        return HollowUiFrame(root = root, nodes = layout.nodes.keys.toList(), layout = layout)
    }

    @Test
    fun `a painted panel blocks click-through, empty space does not`() {
        val panel = BoxNode(modifiers = listOf(Modifier.size(100.px, 100.px).background(UiColor(1f, 0f, 0f, 1f))))
        val root = BoxNode(measurePolicy = UiMeasurePolicies.Column).also { it.children.add(panel) }
        val frame = frameOf(root)
        assertTrue(frame.hitsVisible(50f, 50f), "over the painted panel → blocks")
        assertFalse(frame.hitsVisible(200f, 300f), "over empty space → falls through")
    }

    @Test
    fun `an invisible full-screen container does not block, its painted child does`() {
        // Mirrors the dock space: a full-size container with no background over a small window.
        val window = BoxNode(modifiers = listOf(Modifier.size(60.px, 60.px).background(UiColor(0f, 0f, 1f, 1f))))
        val space = BoxNode(
            id = "space",
            measurePolicy = UiMeasurePolicies.box(UiBoxMode.STACK),
            modifiers = listOf(Modifier.size(UiLength.Fill, UiLength.Fill)),
        ).also { it.children.add(window) }
        val root = BoxNode(measurePolicy = UiMeasurePolicies.Column).also { it.children.add(space) }
        val frame = frameOf(root)
        assertTrue(frame.hitsVisible(30f, 30f), "over the window → blocks")
        assertFalse(frame.hitsVisible(300f, 300f), "over the invisible dock space → falls through to game")
    }

    @Test
    fun `input-transparent lets a painted node pass presses through`() {
        val panel = BoxNode(
            modifiers = listOf(Modifier.size(100.px, 100.px).background(UiColor(1f, 0f, 0f, 1f)).inputTransparent()),
        )
        val root = BoxNode(measurePolicy = UiMeasurePolicies.Column).also { it.children.add(panel) }
        val frame = frameOf(root)
        assertFalse(frame.hitsVisible(50f, 50f), "input-transparent → does not block despite a background")
    }
}
