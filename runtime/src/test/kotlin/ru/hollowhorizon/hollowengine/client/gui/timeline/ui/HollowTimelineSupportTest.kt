package ru.hollowhorizon.hollowengine.client.gui.timeline.ui

import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.ui.autoPanToContentX
import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect
import ru.hollowhorizon.hollowengine.client.ui.scroll.UiScrollHandle
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HollowTimelineSupportTest {
    @Test
    fun `auto pan keeps content inside the viewport edge zone`() {
        val scroll = UiScrollHandle()
        val viewport = UiRect(10f, 20f, 100f, 80f)

        autoPanToContentX(scroll, viewport, 50f)
        assertNull(scroll.pendingX)

        autoPanToContentX(scroll, viewport, 95f)
        assertEquals(20f, scroll.pendingX)
    }

    @Test
    fun `auto pan can return toward the content start`() {
        val scroll = UiScrollHandle().apply { offsetX = 200f }

        autoPanToContentX(scroll, UiRect(0f, 0f, 100f, 80f), 210f)

        assertEquals(185f, scroll.pendingX)
    }
}
