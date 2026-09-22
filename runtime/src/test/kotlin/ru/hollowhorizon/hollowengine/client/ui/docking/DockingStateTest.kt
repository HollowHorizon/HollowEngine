package ru.hollowhorizon.hollowengine.client.ui.docking

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DockingStateTest {
    @Test
    fun `absolute resize recovers immediately after leaving the minimum bound`() {
        val state = DockingState()
        state.openFloating(DockItem("test", "Test"), x = 20f, y = 30f, width = 240f, height = 180f)
        val start = state.floatingWindows.single()

        state.resizeFloatingFrom(start.id, DockResizeEdge.LEFT, start, deltaX = 500f, deltaY = 0f)
        assertEquals(80f, state.floatingWindows.single().width, "dragged past the minimum, it stops there")

        state.resizeFloatingFrom(start.id, DockResizeEdge.LEFT, start, deltaX = 40f, deltaY = 0f)
        val recovered = state.floatingWindows.single()
        assertEquals(60f, recovered.x)
        assertEquals(200f, recovered.width)
    }

    @Test
    fun `pinning takes a tool window out of the tree and unpinning brings it back`() {
        val state = DockingState()
        state.open(DockItem("project", "Project", pinnable = true))
        state.open(DockItem("editor", "Editor"), DockTarget(state.stackIdOf("project"), DockPlacement.RIGHT))

        assertTrue(state.pin("project", DockSide.LEFT))
        assertNull(state.root?.findItem("project"), "a pinned window no longer takes room in the tree")
        assertTrue(state.contains("project"), "but the dock still holds it")
        assertEquals("project", state.expandedOn(DockSide.LEFT)?.item?.id, "pinning opens the panel")

        assertTrue(state.unpin("project"))
        assertNotNull(state.root?.findItem("project"), "unpinning docks it back into the tree")
        assertNull(state.expandedOn(DockSide.LEFT))
        assertTrue(state.pinnedItems.isEmpty())
    }

    @Test
    fun `a stripe button opens its panel and closes it again`() {
        val state = DockingState()
        state.open(DockItem("project", "Project", pinnable = true))
        state.open(DockItem("assets", "Assets", pinnable = true))
        state.pin("project", DockSide.LEFT)
        state.pin("assets", DockSide.LEFT)

        assertEquals("assets", state.expandedOn(DockSide.LEFT)?.item?.id, "one side shows one panel at a time")

        state.togglePinned("assets")
        assertNull(state.expandedOn(DockSide.LEFT), "clicking the open window's button collapses the side")

        state.togglePinned("project")
        assertEquals("project", state.expandedOn(DockSide.LEFT)?.item?.id)
    }

    @Test
    fun `an editor tab cannot be parked on a stripe`() {
        val state = DockingState()
        state.open(DockItem("editor", "Editor"))

        assertFalse(state.pin("editor", DockSide.LEFT))
        assertTrue(state.pinnedItems.isEmpty())
        assertNotNull(state.root?.findItem("editor"), "and it stays where it was")
    }

    @Test
    fun `closing a pinned window takes it off its stripe`() {
        val state = DockingState()
        state.open(DockItem("project", "Project", pinnable = true))
        state.pin("project", DockSide.RIGHT)

        assertTrue(state.close("project"))
        assertTrue(state.pinnedItems.isEmpty())
        assertNull(state.expandedOn(DockSide.RIGHT))
        assertFalse(state.contains("project"))
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
