package ru.hollowhorizon.hollowengine.client.ui.docking

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DockingStateTest {
    @Test
    fun `absolute resize recovers immediately after leaving the minimum bound`() {
        val state = DockingState()
        state.openFloating(DockItem("test", "Test"), x = 20f, y = 30f, width = 240f, height = 180f)
        val start = state.floatingWindows.single()

        state.resizeFloatingFrom(start.id, DockResizeEdge.LEFT, start, deltaX = 500f, deltaY = 0f)
        assertEquals(160f, state.floatingWindows.single().width)

        state.resizeFloatingFrom(start.id, DockResizeEdge.LEFT, start, deltaX = 40f, deltaY = 0f)
        val recovered = state.floatingWindows.single()
        assertEquals(60f, recovered.x)
        assertEquals(200f, recovered.width)
    }
}
