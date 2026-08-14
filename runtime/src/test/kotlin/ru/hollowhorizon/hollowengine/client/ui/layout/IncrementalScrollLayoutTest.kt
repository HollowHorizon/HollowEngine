package ru.hollowhorizon.hollowengine.client.ui.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.scroll.ScrollbarNode
import ru.hollowhorizon.hollowengine.client.ui.scroll.ScrollbarThumbNode
import ru.hollowhorizon.hollowengine.client.ui.scroll.UiScrollHandle
import ru.hollowhorizon.hollowengine.client.ui.scroll.rememberScrollState
import ru.hollowhorizon.hollowengine.client.ui.widgets.TextFieldState
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiCodeEditor
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Scrolling re-places only the subtree that moved. That is only allowed to be faster,
 * so the incremental frame is checked against a from-scratch layout of the same state.
 */
class IncrementalScrollLayoutTest {
    private val code = (1..60).joinToString("\n") { "    fun line$it() = call($it)" }

    private fun scrolledSurface(block: (HollowUiSurface, () -> HollowUiFrame) -> Unit) {
        HollowUiSurface().use { surface ->
            val state = TextFieldState(initialText = code, multiline = true, wrap = false)
            surface.runtime.profiler.enabled = true
            surface.setContent {
                Column(id = "shell", modifier = Modifier.size(100.percent, 100.percent)) {
                    Text("header", id = "header")
                    UiCodeEditor(
                        value = code,
                        onChange = {},
                        state = state,
                        id = "editor",
                        modifier = Modifier.size(300.px, 150.px),
                    )
                    Column(
                        id = "list",
                        modifier = Modifier.size(300.px, 100.px).scrollable(state = rememberScrollState()),
                    ) {
                        repeat(30) { Text("row $it", id = "row-$it") }
                    }
                }
            }
            var now = 0L
            block(surface) {
                now += 16_000_000L
                surface.frame(500f, 400f, 100f, 100f, now)
            }
        }
    }

    @Test
    fun `an incrementally scrolled frame matches a full layout of the same state`() {
        scrolledSurface { surface, frame ->
            repeat(6) { frame() }

            repeat(8) {
                surface.runtime.mouseScrolled(100f, 200f, 0f, -1f, GLFW.GLFW_MOD_CONTROL)
                val incremental = frame()

                val reference = UiLayoutPipeline().compute(incremental.root, 500f, 400f)
                val composed = reference.nodes.filterKeys { it !is ScrollbarNode && it !is ScrollbarThumbNode }
                for ((node, expected) in composed) {
                    val actual = incremental.layout.nodes[node] ?: error("missing ${node.type}/${node.id}")
                    assertEquals(expected.rect, actual.rect, "rect of ${node.type}/${node.id}")
                    assertEquals(expected.content, actual.content, "content of ${node.type}/${node.id}")
                    assertEquals(expected.clip, actual.clip, "clip of ${node.type}/${node.id}")
                    assertEquals(expected.scrollOffset, actual.scrollOffset, "offset of ${node.type}/${node.id}")
                    assertEquals(expected.scrollRange, actual.scrollRange, "range of ${node.type}/${node.id}")
                }
                assertEquals(
                    composed.size,
                    incremental.layout.nodes.count { it.key !is ScrollbarNode && it.key !is ScrollbarThumbNode },
                    "same set of placed nodes",
                )
            }
        }
    }

    @Test
    fun `scrolling places only the subtree that moved`() {
        scrolledSurface { surface, frame ->
            val full = generateSequence { frame().profile!!.placedNodes }.take(6).max()

            surface.runtime.mouseScrolled(100f, 200f, 0f, -1f, GLFW.GLFW_MOD_CONTROL)
            val scrolled = frame().profile!!.placedNodes

            assertTrue(scrolled > 0, "the moved subtree is placed")
            assertTrue(
                scrolled < full,
                "a scroll must place less than the whole screen ($scrolled of $full)",
            )
        }
    }

    @Test
    fun `a composition change still lays out in full`() {
        HollowUiSurface().use { surface ->
            val scroll = UiScrollHandle()
            var rows by mutableStateOf(5)
            surface.setContent {
                Column(id = "list", modifier = Modifier.size(200.px, 80.px).scrollable(state = scroll)) {
                    repeat(rows) { index -> Text("row $index", id = "row-$index") }
                }
            }
            var now = 0L
            repeat(4) { now += 16_000_000L; surface.frame(300f, 300f, -1f, -1f, now) }

            rows = 40
            now += 16_000_000L
            val frame = surface.frame(300f, 300f, -1f, -1f, now)

            val placed = (0 until 40).count { index -> frame.nodes.any { it.id == "row-$index" } }
            assertEquals(40, placed, "rows added by a recomposition are laid out")
        }
    }
}
