package ru.hollowhorizon.hollowengine.client.utils.font

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Checks the packed atlas rather than the field inside one cell, because the last class of artifact
 * lives between cells: the atlas is sampled with linear filtering, and a glyph's quad addresses its
 * cell's exact bounds, so the fragments along the quad's border blend the cell's outermost texels
 * with whatever is packed beside them.
 */
class MsdfAtlasPackingTest {
    @Test
    fun `no cell touches another cell's texels`() {
        val atlas = bakeSample() ?: return
        val cells = atlas.meta.glyphs.filter { !it.isEmpty() }
        assertTrue(cells.size > 10, "the sample baked enough glyphs to pack: ${cells.size}")

        val owner = HashMap<Long, Int>()
        for (glyph in cells) {
            for (y in glyph.atlasBounds.bottom.toInt() until glyph.atlasBounds.top.toInt()) {
                for (x in glyph.atlasBounds.left.toInt() until glyph.atlasBounds.right.toInt()) {
                    owner[y.toLong() * atlas.width + x] = glyph.unicode
                }
            }
        }
        for (glyph in cells) {
            glyph.forEachBorderTexel { x, y -> assertUnowned(owner, atlas, x, y, glyph.unicode) }
        }
    }

    @Test
    fun `the texels around a cell decode as fully outside`() {
        val atlas = bakeSample() ?: return
        for (glyph in atlas.meta.glyphs.filter { !it.isEmpty() }) {
            glyph.forEachBorderTexel { x, y -> assertOutside(atlas, x, y, glyph.unicode) }
        }
    }

    @Test
    fun `cells do not overlap each other`() {
        val atlas = bakeSample() ?: return
        val cells = atlas.meta.glyphs.filter { !it.isEmpty() }
        for (first in cells.indices) {
            for (second in first + 1 until cells.size) {
                val a = cells[first].atlasBounds
                val b = cells[second].atlasBounds
                val overlaps = a.left < b.right && b.left < a.right && a.bottom < b.top && b.bottom < a.top
                assertTrue(
                    !overlaps,
                    "cells for U+${cells[first].unicode.toString(16)} and " +
                            "U+${cells[second].unicode.toString(16)} overlap",
                )
            }
        }
    }

    @Test
    fun `every cell fits inside the atlas with room for its gutter`() {
        val atlas = bakeSample() ?: return
        for (glyph in atlas.meta.glyphs.filter { !it.isEmpty() }) {
            val bounds = glyph.atlasBounds
            assertTrue(bounds.left >= 1f, "U+${glyph.unicode.toString(16)} touches the left border")
            assertTrue(bounds.bottom >= 1f, "U+${glyph.unicode.toString(16)} touches the bottom border")
            assertTrue(
                bounds.right <= atlas.width - 1f,
                "U+${glyph.unicode.toString(16)} touches the right border",
            )
            assertTrue(
                bounds.top <= atlas.height - 1f,
                "U+${glyph.unicode.toString(16)} touches the top border",
            )
        }
    }

    private inline fun MsdfGlyph.forEachBorderTexel(visit: (x: Int, y: Int) -> Unit) {
        val left = atlasBounds.left.toInt()
        val bottom = atlasBounds.bottom.toInt()
        val right = atlasBounds.right.toInt()
        val top = atlasBounds.top.toInt()
        for (x in left - 1..right) {
            visit(x, bottom - 1)
            visit(x, top)
        }
        for (y in bottom - 1..top) {
            visit(left - 1, y)
            visit(right, y)
        }
    }

    private fun assertUnowned(
        owner: Map<Long, Int>,
        atlas: BakedMsdfAtlas,
        x: Int,
        y: Int,
        codepoint: Int,
    ) {
        if (x < 0 || y < 0 || x >= atlas.width || y >= atlas.height) return
        val holder = owner[y.toLong() * atlas.width + x] ?: return
        assertEquals(
            holder, codepoint, "the texel at ($x, $y) beside U+${codepoint.toString(16)} belongs to " +
                    "U+${holder.toString(16)}: a linear sample at the quad's edge would reach it"
        )
    }

    private fun assertOutside(atlas: BakedMsdfAtlas, x: Int, y: Int, codepoint: Int) {
        if (x < 0 || y < 0 || x >= atlas.width || y >= atlas.height) return
        val median = atlas.medianAt(x, y)
        assertTrue(
            median < 128,
            "texel ($x, $y) next to U+${codepoint.toString(16)} reads as ink (median $median): " +
                    "a linear sample at the quad's edge would bleed it in",
        )
    }

    private fun bakeSample(): BakedMsdfAtlas? {
        val face = openTestFace() ?: return null
        return face.use { face ->
            bakeMsdfAtlas(face, MsdfBakeSpec(48f, 2f, (0x20..0x7E).toList().toIntArray()))
        }
    }
}
