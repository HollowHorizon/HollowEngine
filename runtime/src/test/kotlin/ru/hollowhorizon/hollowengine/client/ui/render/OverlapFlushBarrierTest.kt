package ru.hollowhorizon.hollowengine.client.ui.render

import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.ui.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Overlapping visual subtrees of an overlap-capable container get a flush barrier so phase batching
 * can't reorder one over another; empty, flow-layout and separated children batch without one.
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

    @Test
    fun `overlapping empty children do not split batches`() {
        val root = BoxNode(measurePolicy = UiMeasurePolicies.box(UiBoxMode.STACK)).also { node ->
            repeat(1_000) { index ->
                node.children += BoxNode(
                    id = "empty-$index",
                    modifiers = listOf(Modifier.size(60.px, 60.px)),
                )
            }
        }

        assertEquals(emptyList(), commandsFor(root))
    }

    @Test
    fun `transparent rounded children do not emit geometry or split batches`() {
        val root = BoxNode(measurePolicy = UiMeasurePolicies.box(UiBoxMode.STACK)).also { node ->
            repeat(1_000) { index ->
                node.children += BoxNode(
                    id = "transparent-$index",
                    modifiers = listOf(
                        Modifier
                            .size(60.px, 60.px)
                            .background(UiColor.Transparent)
                            .borderRadius(4f),
                    ),
                )
            }
        }

        assertEquals(emptyList(), commandsFor(root))
    }

    @Test
    fun `visible rounded background still emits geometry`() {
        val root = BoxNode(measurePolicy = UiMeasurePolicies.box(UiBoxMode.STACK)).also { node ->
            node.children += BoxNode(
                id = "visible-rounded",
                modifiers = listOf(
                    Modifier
                        .size(60.px, 60.px)
                        .background(UiColor(1f, 1f, 1f, 0.01f))
                        .borderRadius(4f),
                ),
            )
        }

        assertEquals(1, commandsFor(root).count { it is DrawBoxCommand })
    }

    @Test
    fun `empty child does not add barriers between visual siblings`() {
        val root = BoxNode(measurePolicy = UiMeasurePolicies.box(UiBoxMode.STACK)).also { node ->
            node.children += box("a")
            node.children += BoxNode(id = "empty", modifiers = listOf(Modifier.size(60.px, 60.px)))
            node.children += box("b")
        }

        assertEquals(1, commandsFor(root).count { it is FlushBarrierCommand })
    }

    @Test
    fun `visual descendant keeps its wrapper in overlap ordering`() {
        val wrapper = BoxNode(
            id = "wrapper",
            measurePolicy = UiMeasurePolicies.box(UiBoxMode.STACK),
            modifiers = listOf(Modifier.size(60.px, 60.px)),
        ).also { it.children += box("nested") }
        val root = BoxNode(measurePolicy = UiMeasurePolicies.box(UiBoxMode.STACK)).also { node ->
            node.children += box("a")
            node.children += wrapper
        }

        assertEquals(1, commandsFor(root).count { it is FlushBarrierCommand })
    }
}
