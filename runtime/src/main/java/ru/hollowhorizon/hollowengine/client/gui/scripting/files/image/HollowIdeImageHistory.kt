package ru.hollowhorizon.hollowengine.client.gui.scripting.files.image

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap

internal class HollowIdeImageEdit {
    private val originalPixels = Int2IntOpenHashMap().apply { defaultReturnValue(Int.MIN_VALUE) }

    val isEmpty: Boolean get() = originalPixels.isEmpty()

    fun record(index: Int, color: Int) {
        if (!originalPixels.containsKey(index)) originalPixels.put(index, color)
    }

    fun build(readPixel: (Int) -> Int): HollowIdePixelChange {
        val indices = IntArray(originalPixels.size)
        val before = IntArray(originalPixels.size)
        val after = IntArray(originalPixels.size)
        val iterator = originalPixels.int2IntEntrySet().fastIterator()
        var position = 0
        while (iterator.hasNext()) {
            val entry = iterator.next()
            indices[position] = entry.intKey
            before[position] = entry.intValue
            after[position] = readPixel(entry.intKey)
            position++
        }
        return HollowIdePixelChange(indices, before, after)
    }
}

internal data class HollowIdePixelChange(
    val indices: IntArray,
    val before: IntArray,
    val after: IntArray,
) {
    val size: Int get() = indices.size

    fun applyBefore(writePixel: (Int, Int) -> Unit) = apply(before, writePixel)

    fun applyAfter(writePixel: (Int, Int) -> Unit) = apply(after, writePixel)

    private fun apply(colors: IntArray, writePixel: (Int, Int) -> Unit) {
        for (index in indices.indices) writePixel(indices[index], colors[index])
    }
}

internal class HollowIdeImageHistory(
    private val maxActions: Int = DefaultMaxActions,
    private val maxChangedPixels: Int = DefaultMaxChangedPixels,
) {
    private val actions = ArrayList<HollowIdePixelChange>()
    private var cursor = 0
    private var savedCursor = 0
    private var changedPixels = 0
    private var state by mutableStateOf(HollowIdeImageHistoryState())

    val canUndo: Boolean get() = state.canUndo
    val canRedo: Boolean get() = state.canRedo
    val isModified: Boolean get() = state.isModified

    fun push(change: HollowIdePixelChange) {
        if (change.size == 0) return
        discardRedo()
        actions += change
        cursor++
        changedPixels += change.size
        trimToLimits()
        publishState()
    }

    fun undo(writePixel: (Int, Int) -> Unit): Boolean {
        if (!canUndo) return false
        cursor--
        actions[cursor].applyBefore(writePixel)
        publishState()
        return true
    }

    fun redo(writePixel: (Int, Int) -> Unit): Boolean {
        if (!canRedo) return false
        actions[cursor].applyAfter(writePixel)
        cursor++
        publishState()
        return true
    }

    fun markSaved() {
        savedCursor = cursor
        publishState()
    }

    fun clear() {
        actions.clear()
        cursor = 0
        savedCursor = 0
        changedPixels = 0
        publishState()
    }

    private fun discardRedo() {
        if (cursor == actions.size) return
        if (savedCursor > cursor) savedCursor = UnknownSavedCursor
        while (actions.size > cursor) {
            changedPixels -= actions.removeAt(actions.lastIndex).size
        }
    }

    private fun trimToLimits() {
        while (actions.size > maxActions || (changedPixels > maxChangedPixels && actions.size > 1)) {
            changedPixels -= actions.removeAt(0).size
            cursor--
            savedCursor = when {
                savedCursor == UnknownSavedCursor -> UnknownSavedCursor
                savedCursor == 0 -> UnknownSavedCursor
                else -> savedCursor - 1
            }
        }
    }

    private fun publishState() {
        state = HollowIdeImageHistoryState(
            canUndo = cursor > 0,
            canRedo = cursor < actions.size,
            isModified = cursor != savedCursor,
        )
    }
}

private data class HollowIdeImageHistoryState(
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isModified: Boolean = false,
)

private const val DefaultMaxActions = 128
private const val DefaultMaxChangedPixels = 4 * 1024 * 1024
private const val UnknownSavedCursor = -1
