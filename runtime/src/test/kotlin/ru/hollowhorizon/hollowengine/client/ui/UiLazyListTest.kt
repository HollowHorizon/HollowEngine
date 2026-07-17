package ru.hollowhorizon.hollowengine.client.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UiLazyListTest {
    @Test
    fun `cross axis scroll exposes both ranges for wide lazy column rows`() {
        val state = LazyListState()
        HollowUiSurface().use { surface ->
            surface.setContent {
                LazyColumn(
                    id = "list",
                    state = state,
                    crossAxisScroll = true,
                    modifier = Modifier.size(100.px, 100.px),
                ) {
                    items(20) {
                        Box(modifier = Modifier.size(300.px, 20.px))
                    }
                }
            }

            surface.frame(120f, 120f, -1f, -1f, 0L)
            val frame = surface.frame(120f, 120f, -1f, -1f, 16_000_000L)
            val list = frame.nodes.single { it.id == "list" }
            val layout = frame.layout[list]

            assertTrue(layout.scrollRange.x > 0f)
            assertTrue(layout.scrollRange.y > 0f)

            state.scroll.scrollTo(y = Float.MAX_VALUE)
            val scrolledFrame = surface.frame(120f, 120f, -1f, -1f, 32_000_000L)
            val scrolledLayout = scrolledFrame.layout[list]
            assertEquals(scrolledLayout.scrollRange.y, scrolledLayout.scrollOffset.y)
        }
    }
}
