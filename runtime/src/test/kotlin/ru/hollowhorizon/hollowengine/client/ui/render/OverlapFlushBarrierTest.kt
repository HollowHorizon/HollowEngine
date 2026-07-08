package ru.hollowhorizon.hollowengine.client.ui.render

import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.ui.*
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Overlapping children of an overlap-capable container get a flush barrier so phase batching can't
 * reorder one over another; flow layouts and separated children batch without one.
 */
class OverlapFlushBarrierTest {
    private fun commandsFor(root: BoxNode): List<UiRenderCommand> {
        val frame = HollowUiRuntime().frame(root, 200f, 200f, -1f, -1f, 0L)
        return UiCommandRenderer().collect(frame.root, frame.layout)
    }

    private fun box(id: String, x: Int = 0, y: Int = 0) = BoxNode(
        id = id,
        modifiers = listOf(Modifier.size(60.px, 60.px).position(x.px, y.px).background(UiColor.White)),
    )

    @Test
    fun `overlapping stack children get a flush barrier between them`() {
        val root = BoxNode(measurePolicy = UiMeasurePolicies.box(UiBoxMode.STACK))
            .also { it.children.add(box("a")); it.children.add(box("b")) }
        assertTrue(commandsFor(root).any { it is FlushBarrierCommand }, "stacked children overlap")
    }

    @Test
    fun `separated free children batch without a barrier`() {
        val root = BoxNode(measurePolicy = UiMeasurePolicies.box(UiBoxMode.FREE))
            .also { it.children.add(box("a", 0, 0)); it.children.add(box("b", 120, 120)) }
        assertFalse(commandsFor(root).any { it is FlushBarrierCommand }, "far-apart children do not overlap")
    }

    @Test
    fun `column children never get a barrier`() {
        val root = BoxNode(measurePolicy = UiMeasurePolicies.Column)
            .also { it.children.add(box("a")); it.children.add(box("b")) }
        assertFalse(commandsFor(root).any { it is FlushBarrierCommand }, "flow layout never overlaps")
    }
}
