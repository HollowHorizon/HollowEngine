package ru.hollowhorizon.hollowengine.client.utils.font

import kotlin.math.min
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TtfBakeConsistencyTest {
    private val spec = MsdfBakeSpec(pixelSize = 48f, pixelRange = 2f, codepoints = IntArray(0))

    @Test
    fun `the baked field agrees with the winding rule away from edges`() {
        val face = openTestFace() ?: return
        face.use {
            for (char in "инЖымФоBряжщ&@S") {
                val glyph = bakeGlyphField(face, char.code, spec) ?: continue
                var checked = 0
                for (py in 0 until glyph.height) {
                    for (px in 0 until glyph.width) {
                        val x = (px + 0.5f) / glyph.scale - glyph.translateX
                        val y = (py + 0.5f) / glyph.scale - glyph.translateY
                        val distance = glyph.shape.distanceToOutline(x, y) * glyph.scale
                        if (distance < SafeDistancePixels) continue
                        checked++
                        val inside = glyph.shape.containsByWinding(x, y)
                        val median = glyph.values.medianAt(px, py, glyph.width)
                        assertEquals(
                            (median > 0.5f), inside, "glyph '$char' pixel ($px, $py): winding says inside=$inside but the " +
                                    "field reads $median at ${"%.2f".format(distance)}px from the outline"
                        )
                    }
                }
                assertTrue(checked > 0, "glyph '$char' had pixels to check")
            }
        }
    }

    @Test
    fun `a faux-bold bias does not conjure ink far outside the glyph`() {
        val face = openTestFace() ?: return
        face.use {
            val bias = 0.2f
            val clearance = bias * spec.pixelRange + SafeDistancePixels
            for (char in "инЖы") {
                val glyph = bakeGlyphField(face, char.code, spec) ?: continue
                for (py in 0 until glyph.height) {
                    for (px in 0 until glyph.width) {
                        val x = (px + 0.5f) / glyph.scale - glyph.translateX
                        val y = (py + 0.5f) / glyph.scale - glyph.translateY
                        if (glyph.shape.containsByWinding(x, y)) continue
                        val outlineDistance = glyph.shape.distanceToOutline(x, y)
                        if (outlineDistance * glyph.scale < clearance) continue
                        if (glyph.shape.distanceToNearestVertex(x, y) - outlineDistance < 1.5f / glyph.scale) continue
                        val median = glyph.values.medianAt(px, py, glyph.width)
                        assertTrue(
                            median - 0.5f + bias < 0f,
                            "glyph '$char' pixel ($px, $py): biased coverage appears " +
                                    "${"%.2f".format(outlineDistance * glyph.scale)}px outside (median $median)",
                        )
                    }
                }
            }
        }
    }

    private fun MsdfShape.distanceToNearestVertex(x: Float, y: Float): Float {
        var best = Float.MAX_VALUE
        for (contour in contours) {
            for (edge in contour.edges) {
                val dx = edge.startX - x
                val dy = edge.startY - y
                best = min(best, sqrt(dx * dx + dy * dy))
            }
        }
        return best
    }

    private companion object {
        const val SafeDistancePixels = 1.25f
    }
}
