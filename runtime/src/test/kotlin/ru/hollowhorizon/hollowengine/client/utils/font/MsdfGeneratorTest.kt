package ru.hollowhorizon.hollowengine.client.utils.font

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The distance-field generator behind `ttf:` fonts, exercised on shapes whose exact distances are
 * known by hand. Everything here is pure geometry, so it runs without a game or a GL context, which
 * is the only place the baking maths can be pinned down at all.
 */
class MsdfGeneratorTest {
    /** A square from (8,8) to (24,24) rendered into a 32x32 field with a range of 8 units. */
    private fun squareField(range: Float = 8f): FloatArray {
        val shape = squareShape(left = 8f, bottom = 8f, size = 16f)
        val output = FloatArray(FieldSize * FieldSize * 3)
        generateMsdf(shape, FieldSize, FieldSize, scale = 1f, translateX = 0f, translateY = 0f, range, output)
        return output
    }

    @Test
    fun `distance is positive inside the shape and negative outside`() {
        val field = squareField()
        assertTrue(field.median(16, 16) > 0.5f, "the centre is inside: ${field.median(16, 16)}")
        assertTrue(field.median(2, 2) < 0.5f, "the far corner is outside: ${field.median(2, 2)}")
        assertTrue(field.median(16, 2) < 0.5f, "below the square is outside: ${field.median(16, 2)}")
    }

    @Test
    fun `the encoded distance crosses one half exactly on the edge`() {
        val field = squareField()
        // Pixel centres sit at x + 0.5, so columns 7 and 8 straddle the edge at x = 8 by half a
        // unit each way; with a range of 8 that is 1/16 of the encoded scale.
        assertEquals(0.5f - 0.5f / 8f, field.median(7, 16), Tolerance)
        assertEquals(0.5f + 0.5f / 8f, field.median(8, 16), Tolerance)
    }

    @Test
    fun `distance grows linearly with depth into the shape`() {
        val field = squareField()
        // Four units in from the left edge, still nearer to it than to any other edge.
        assertEquals(0.5f + 3.5f / 8f, field.median(11, 16), Tolerance)
        assertEquals(0.5f + 5.5f / 8f, field.median(13, 16), Tolerance)
    }

    @Test
    fun `each side of a corner keeps one channel to itself`() {
        val shape = squareShape(left = 8f, bottom = 8f, size = 16f)
        val colors = shape.contours.single().edges.map { it.color }
        assertTrue(colors.all { it.channelCount() == 2 }, "every edge takes two of three channels: $colors")
        for (index in colors.indices) {
            val next = colors[(index + 1) % colors.size]
            assertTrue(colors[index] != next, "adjacent edges of a corner differ: $colors")
        }
    }

    @Test
    fun `a corner reads sharp because the channels disagree there`() {
        val field = squareField()
        val spread = field.channelSpreadAt(5, 7, FieldSize)
        assertTrue(spread > 0.2f, "channels disagree at a corner (spread $spread)")
        assertTrue(field.median(5, 7) < 0.5f, "the corner's outside stays outside: ${field.median(5, 7)}")
    }

    @Test
    fun `a hole reads as outside while the ring around it reads as inside`() {
        val shape = MsdfShape()
        shape.contours += squareContour(left = 4f, bottom = 4f, size = 24f)
        shape.contours += squareContour(left = 12f, bottom = 12f, size = 8f)
        shape.contours[1].reverse()
        shape.orientForPositiveInside()
        shape.colorEdges()
        val output = FloatArray(FieldSize * FieldSize * 3)
        generateMsdf(shape, FieldSize, FieldSize, scale = 1f, translateX = 0f, translateY = 0f, range = 8f, output)
        assertTrue(output.median(16, 16) < 0.5f, "the hole is outside: ${output.median(16, 16)}")
        assertTrue(output.median(8, 16) > 0.5f, "the ring is inside: ${output.median(8, 16)}")
    }

    @Test
    fun `orientation is normalized whichever way the outline was wound`() {
        val clockwise = squareShape(left = 8f, bottom = 8f, size = 16f, counterClockwise = false)
        val counterClockwise = squareShape(left = 8f, bottom = 8f, size = 16f, counterClockwise = true)
        val first = FloatArray(FieldSize * FieldSize * 3)
        val second = FloatArray(FieldSize * FieldSize * 3)
        generateMsdf(clockwise, FieldSize, FieldSize, 1f, 0f, 0f, 8f, first)
        generateMsdf(counterClockwise, FieldSize, FieldSize, 1f, 0f, 0f, 8f, second)
        assertTrue(first.median(16, 16) > 0.5f && second.median(16, 16) > 0.5f, "both read as inside")
    }

    @Test
    fun `a flattened curve is not mistaken for a run of corners`() {
        val contour = MsdfContour()
        val curve = FloatArray(2 * 9) { index ->
            val step = index / 2
            if (index % 2 == 0) 8f + step else 8f + step * step * 0.02f
        }
        contour.edges += MsdfEdge(curve)
        contour.edges += MsdfEdge(floatArrayOf(curve[curve.size - 2], curve[curve.size - 1], 8f, 8f))
        val shape = MsdfShape().apply { contours += contour }
        shape.colorEdges()
        assertEquals(2, shape.contours.single().edges.size, "the curve stays one edge")
    }

    private fun FloatArray.median(x: Int, y: Int): Float = medianAt(x, y, FieldSize)

    private fun Int.channelCount(): Int = (0..2).count { this shr it and 1 == 1 }

    private companion object {
        const val FieldSize = 32
        const val Tolerance = 1e-4f
    }
}
