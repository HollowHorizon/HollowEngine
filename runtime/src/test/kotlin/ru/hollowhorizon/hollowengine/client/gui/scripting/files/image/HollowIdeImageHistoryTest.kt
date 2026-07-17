package ru.hollowhorizon.hollowengine.client.gui.scripting.files.image

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HollowIdeImageHistoryTest {
    @Test
    fun `undo and redo restore sparse pixel changes`() {
        val pixels = intArrayOf(10, 20, 30, 40)
        val history = HollowIdeImageHistory()
        pixels[1] = 21
        pixels[3] = 41
        history.push(
            HollowIdePixelChange(
                indices = intArrayOf(1, 3),
                before = intArrayOf(20, 40),
                after = intArrayOf(21, 41),
            ),
        )

        assertTrue(history.canUndo)
        assertTrue(history.undo(pixels::set))
        assertContentEquals(intArrayOf(10, 20, 30, 40), pixels)
        assertTrue(history.canRedo)

        assertTrue(history.redo(pixels::set))
        assertContentEquals(intArrayOf(10, 21, 30, 41), pixels)
    }

    @Test
    fun `saved state follows undo redo and branching`() {
        val pixels = intArrayOf(1)
        val history = HollowIdeImageHistory()
        pixels[0] = 2
        history.push(HollowIdePixelChange(intArrayOf(0), intArrayOf(1), intArrayOf(2)))
        history.markSaved()
        assertFalse(history.isModified)

        history.undo(pixels::set)
        assertTrue(history.isModified)
        history.redo(pixels::set)
        assertFalse(history.isModified)

        history.undo(pixels::set)
        pixels[0] = 3
        history.push(HollowIdePixelChange(intArrayOf(0), intArrayOf(1), intArrayOf(3)))
        assertFalse(history.canRedo)
        assertTrue(history.isModified)
    }

    @Test
    fun `history availability invalidates its compose readers`() {
        val history = HollowIdeImageHistory()
        var invalidated = false
        val observer = SnapshotStateObserver { command -> command() }
        observer.start()
        try {
            observer.observeReads(Unit, { invalidated = true }) {
                history.canUndo
                history.canRedo
            }
            history.push(HollowIdePixelChange(intArrayOf(0), intArrayOf(1), intArrayOf(2)))
            Snapshot.sendApplyNotifications()
            assertTrue(invalidated)

            invalidated = false
            observer.observeReads(Unit, { invalidated = true }) {
                history.canUndo
                history.canRedo
            }
            history.undo { _, _ -> }
            Snapshot.sendApplyNotifications()
            assertTrue(invalidated)
            assertTrue(history.canRedo)
        } finally {
            observer.stop()
        }
    }
}
