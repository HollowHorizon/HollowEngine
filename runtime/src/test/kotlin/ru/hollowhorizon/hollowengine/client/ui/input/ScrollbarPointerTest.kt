package ru.hollowhorizon.hollowengine.client.ui.input

import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.scroll.UiScrollHandle
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The bar has to answer the mouse and wheel. A container with padding used to clip its
 * own bar out of the hit test, and the track carried no input capabilities at all, so both the
 * jump-to-spot press and the thumb drag silently did nothing.
 */
class ScrollbarPointerTest {
    private fun surfaceWithScrollbar(block: (HollowUiSurface, UiScrollHandle, HollowUiFrame, UiNode) -> Unit) {
        HollowUiSurface().use { surface ->
            val scroll = UiScrollHandle()
            surface.setContent {
                Box(
                    id = "viewport",
                    mode = UiBoxMode.STACK,
                    modifier = Modifier.size(100.px, 100.px).padding(10.px).then(scrollModifier(horizontal = false, state = scroll)),
                ) {
                    Box(modifier = Modifier.size(60.px, 400.px))
                }
            }
            surface.frame(200f, 200f, -1f, -1f, 0L)
            val frame = surface.frame(200f, 200f, -1f, -1f, 16_000_000L)
            val viewport = frame.nodes.single { it.id == "viewport" }
            block(surface, scroll, frame, viewport)
        }
    }

    @Test
    fun `pressing the track below the thumb scrolls towards the pointer`() {
        surfaceWithScrollbar { surface, scroll, frame, viewport ->
            val bar = frame.layout.scrollbars.getValue(viewport).single()
            val track = frame.layout.nodes.getValue(bar).rect
            val thumb = frame.layout.nodes.getValue(bar.thumb).rect
            val pressY = (thumb.y + thumb.height + track.y + track.height) / 2f

            assertEquals(0f, scroll.offsetY, 0.01f, "starts at the top")
            val handled = surface.runtime.mouseClicked(track.x + track.width / 2f, pressY, 0)
            surface.frame(200f, 200f, -1f, -1f, 32_000_000L)

            assertTrue(handled, "the track press must be handled")
            assertTrue(scroll.offsetY > 0f, "the press must scroll down (offset=${scroll.offsetY})")
        }
    }

    @Test
    fun `dragging the thumb to the bottom scrolls to the end`() {
        surfaceWithScrollbar { surface, scroll, frame, viewport ->
            val bar = frame.layout.scrollbars.getValue(viewport).single()
            val track = frame.layout.nodes.getValue(bar).rect
            val thumb = frame.layout.nodes.getValue(bar.thumb).rect
            val x = track.x + track.width / 2f

            surface.runtime.mouseClicked(x, thumb.y + thumb.height / 2f, 0)
            surface.runtime.mouseDragged(x, track.y + track.height, 0, 0f, track.height, modifiers = 0)
            surface.frame(200f, 200f, -1f, -1f, 32_000_000L)

            assertEquals(scroll.range.y, scroll.offsetY, 0.01f, "dragging to the track end pins the content")
        }
    }
}
