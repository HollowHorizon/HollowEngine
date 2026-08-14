package ru.hollowhorizon.hollowengine.client.utils.font

/** A cell handed out by [GlyphAtlasShelves], in atlas pixels. */
internal data class GlyphAtlasSlot(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    /** The shelf this came from; [GlyphAtlasShelves.release] needs it to give the space back. */
    internal val shelf: Int,
) {
    fun overlaps(other: GlyphAtlasSlot): Boolean =
        x < other.x + other.width && other.x < x + width &&
                y < other.y + other.height && other.y < y + height
}

/**
 * Places glyph cells into a fixed-size atlas page, one shelf at a time.
 */
internal class GlyphAtlasShelves(
    val width: Int,
    val height: Int,
    private val gutter: Int = DefaultGutter,
) {
    private val shelves = ArrayList<Shelf>()
    private var usedHeight = gutter

    /**
     * A cell with room for [cellWidth] x [cellHeight], or null when the page has no room left. A
     * reused cell may come back larger than asked for; only its origin and the requested size are
     * meant to be drawn into.
     */
    fun allocate(cellWidth: Int, cellHeight: Int): GlyphAtlasSlot? {
        if (cellWidth <= 0 || cellHeight <= 0) return null
        if (cellWidth + 2 * gutter > width) return null
        reuse(cellWidth, cellHeight)?.let { return it }

        var best: Shelf? = null
        for (index in shelves.indices) {
            val shelf = shelves[index]
            if (shelf.height < cellHeight) continue
            if (shelf.cursorX + cellWidth + gutter > width) continue
            if (best == null || shelf.height < best.height) best = shelf
        }
        best?.let { return it.take(cellWidth, cellHeight) }

        if (usedHeight + cellHeight + gutter > height) return null
        val shelf = Shelf(index = shelves.size, y = usedHeight, height = cellHeight)
        shelves += shelf
        usedHeight += cellHeight + gutter
        return shelf.take(cellWidth, cellHeight)
    }

    /** Gives [slot] back so a later cell that fits inside it can take its place. */
    fun release(slot: GlyphAtlasSlot) {
        shelves.getOrNull(slot.shelf)?.free?.plusAssign(slot)
    }

    private fun reuse(cellWidth: Int, cellHeight: Int): GlyphAtlasSlot? {
        for (shelfIndex in shelves.indices) {
            val free = shelves[shelfIndex].free
            var bestIndex = -1
            var bestArea = Int.MAX_VALUE
            for (index in free.indices) {
                val candidate = free[index]
                if (candidate.width < cellWidth || candidate.height < cellHeight) continue
                val area = candidate.width * candidate.height
                if (area < bestArea) {
                    bestArea = area
                    bestIndex = index
                }
            }
            if (bestIndex < 0) continue
            val hole = free.removeAt(bestIndex)
            return hole.copy(shelf = shelfIndex)
        }
        return null
    }

    private inner class Shelf(val index: Int, val y: Int, val height: Int) {
        var cursorX = gutter
        val free = ArrayList<GlyphAtlasSlot>()

        fun take(cellWidth: Int, cellHeight: Int): GlyphAtlasSlot {
            val slot = GlyphAtlasSlot(cursorX, y, cellWidth, cellHeight, index)
            cursorX += cellWidth + gutter
            return slot
        }
    }

    private companion object {
        const val DefaultGutter = 2
    }
}
