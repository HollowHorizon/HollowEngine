package ru.hollowhorizon.hollowengine.client.ui.docking

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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

    @Test
    fun `sizing a new editor split leaves an existing bottom panel unchanged`() {
        val state = DockingState()
        state.open(DockItem("project", "Project"))
        val projectStack = assertNotNull(state.stackIdOf("project"))
        state.open(DockItem("assets", "Assets"), DockTarget(projectStack, DockPlacement.BOTTOM))

        val verticalRoot = state.root as DockNode.Split
        state.setSplitFraction(verticalRoot.id, 0.7f)
        state.open(DockItem("editor", "Editor"), DockTarget(projectStack, DockPlacement.RIGHT))

        assertTrue(state.setSplitFractionForItem("project", "editor", 0.28f))
        val preservedRoot = state.root as DockNode.Split
        assertEquals(verticalRoot.id, preservedRoot.id)
        assertEquals(0.7f, preservedRoot.fraction)

        val editorSplit = assertNotNull(preservedRoot.findSplitSeparating("project", "editor"))
        assertEquals(0.28f, editorSplit.fraction)
    }
}
