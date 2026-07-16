package ru.hollowhorizon.hollowengine.client.ui.render

import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.ui.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Overlapping visual subtrees of an overlap-capable container get a flush barrier only when phase
 * batching would reorder them, a later sibling painting in an earlier phase (e.g. an opaque
 * background) under an earlier sibling's text.
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
    fun `later background over earlier text gets a flush barrier`() {
        val root = BoxNode(measurePolicy = UiMeasurePolicies.box(UiBoxMode.STACK))
            .also { it.children.add(SpanNode("under")); it.children.add(box("cover")) }
        assertEquals(1, commandsFor(root).count { it is FlushBarrierCommand }, "background inverts text")
    }

    @Test
    fun `clip inside an overlap sibling survives after its background`() {
        val clipped = BoxNode(
            id = "clipped",
            modifiers = listOf(Modifier.size(60.px, 60.px).background(UiColor.White).clip()),
        ).also { it.children += box("inner") }
        val root = BoxNode(measurePolicy = UiMeasurePolicies.box(UiBoxMode.STACK)).also { node ->
            node.children += box("a")
            node.children += clipped
        }

        val commands = commandsFor(root)
        assertEquals(1, commands.count { it is PushClipCommand }, "clip push survives")
        assertEquals(1, commands.count { it is PopClipCommand }, "clip pop survives")
    }

    @Test
    fun `overlapping same-phase backgrounds batch without a barrier`() {
        val root = BoxNode(measurePolicy = UiMeasurePolicies.box(UiBoxMode.STACK))
            .also { it.children.add(box("a")); it.children.add(box("b")) }
        assertFalse(commandsFor(root).any { it is FlushBarrierCommand }, "same phase needs no barrier")
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
            node.children += SpanNode("under")
            node.children += BoxNode(id = "empty", modifiers = listOf(Modifier.size(60.px, 60.px)))
            node.children += box("cover")
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
            node.children += SpanNode("under")
            node.children += wrapper
        }

        assertEquals(1, commandsFor(root).count { it is FlushBarrierCommand })
    }
}
