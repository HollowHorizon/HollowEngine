package ru.hollowhorizon.hollowengine.client.utils.font

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Edge coloring decides which of the three channels each edge writes to, and the median of the
 * three is what reconstructs a sharp corner.
 */
class MsdfEdgeColoringTest {
    @Test
    fun `a contour with a single corner still uses all three channels`() {
        val shape = teardrop()
        assertEquals(1, cornerCount(shape), "the fixture really is the one-corner case")
        shape.colorEdges()
        assertEquals(MsdfChannel.WHITE, shape.usedChannels(), "a channel was left with no edge")
    }

    @Test
    fun `a single corner splits the contour into three colour bands`() {
        val shape = teardrop()
        shape.colorEdges()
        val colors = shape.contours.single().edges.map { it.color }
        assertEquals(3, colors.toSet().size, "three bands, not one colour for the whole loop: $colors")
    }

    @Test
    fun `a corner keeps exactly one channel in common across it`() {
        val shape = teardrop()
        shape.colorEdges()
        val edges = shape.contours.single().edges
        for (index in edges.indices) {
            val previous = edges[(index - 1 + edges.size) % edges.size]
            val common = previous.color and edges[index].color
            assertTrue(common != 0, "neighbouring edges share a channel, or the join vanishes")
        }
    }

    @Test
    fun `a smooth contour uses all three channels`() {
        val shape = MsdfShape().apply { contours += circleContour(segmentsPerEdge = 8, edges = 4) }
        assertEquals(0, cornerCount(shape), "a circle has no corners")
        shape.colorEdges()
        assertEquals(MsdfChannel.WHITE, shape.usedChannels())
    }

    @Test
    fun `a square's four corners still alternate colours`() {
        val shape = MsdfShape().apply { contours += squareContour(left = 0f, bottom = 0f, size = 10f) }
        shape.colorEdges()
        val colors = shape.contours.single().edges.map { it.color }
        assertEquals(MsdfChannel.WHITE, shape.usedChannels())
        for (index in colors.indices) {
            assertTrue(colors[index] != colors[(index + 1) % colors.size], "adjacent edges differ: $colors")
        }
    }

    @Test
    fun `splitting an edge in thirds keeps its ends and its shape`() {
        val edge = MsdfEdge(floatArrayOf(0f, 0f, 3f, 0f, 6f, 3f, 9f, 3f))
        val parts = edge.splitInThirds()

        assertEquals(3, parts.size)
        assertEquals(edge.startX, parts[0].startX)
        assertEquals(edge.startY, parts[0].startY)
        assertEquals(edge.endX, parts[2].endX)
        assertEquals(edge.endY, parts[2].endY)
        for (index in 0 until parts.size - 1) {
            assertEquals(parts[index].endX, parts[index + 1].startX, "part ${index + 1} starts where $index ended")
            assertEquals(parts[index].endY, parts[index + 1].startY)
        }
    }

    @Test
    fun `a colouring that would drop a channel falls back to a plain field`() {
        val shape = MsdfShape()
        shape.contours += MsdfContour().apply {
            edges += MsdfEdge(floatArrayOf(0f, 0f, 10f, 0f)).also { it.color = MsdfChannel.CYAN }
            edges += MsdfEdge(floatArrayOf(10f, 0f, 0f, 0f)).also { it.color = MsdfChannel.CYAN }
        }
        shape.colorEdges()
        assertEquals(MsdfChannel.WHITE, shape.usedChannels())
    }

    /** Corners as the colourer itself counts them, at the threshold it actually runs with. */
    private fun cornerCount(shape: MsdfShape): Int =
        findCorners(shape.contours.single().edges, sin(DefaultAngleThreshold)).size

    /**
     * A circle with a spike: the two straight edges are tangent to the arc, so they join it smoothly
     * and the only corner in the whole loop is the apex where they meet. This is exactly the shape
     * msdfgen names the teardrop case.
     */
    private fun teardrop(radius: Float = 5f, apexX: Float = 10f): MsdfShape {
        val alpha = kotlin.math.acos((radius / apexX).toDouble())
        val upper = doubleArrayOf(radius * cos(alpha), radius * sin(alpha))
        val lower = doubleArrayOf(radius * cos(alpha), -radius * sin(alpha))
        val steps = 24
        val arc = ArrayList<Float>()
        for (step in 0..steps) {
            val angle = alpha + (2 * PI - 2 * alpha) * step / steps
            arc += (radius * cos(angle)).toFloat()
            arc += (radius * sin(angle)).toFloat()
        }
        return MsdfShape().apply {
            contours += MsdfContour().apply {
                edges += MsdfEdge(arc.toFloatArray())
                edges += MsdfEdge(floatArrayOf(lower[0].toFloat(), lower[1].toFloat(), apexX, 0f))
                edges += MsdfEdge(floatArrayOf(apexX, 0f, upper[0].toFloat(), upper[1].toFloat()))
            }
        }
    }

    private fun circleContour(segmentsPerEdge: Int, edges: Int): MsdfContour {
        val contour = MsdfContour()
        val total = segmentsPerEdge * edges
        for (edge in 0 until edges) {
            val points = ArrayList<Float>()
            for (step in 0..segmentsPerEdge) {
                val angle = 2 * PI * (edge * segmentsPerEdge + step) / total
                points += (10 * cos(angle)).toFloat()
                points += (10 * sin(angle)).toFloat()
            }
            val startAngle = 2 * PI * (edge * segmentsPerEdge) / total
            val endAngle = 2 * PI * ((edge + 1) * segmentsPerEdge) / total
            contour.edges += MsdfEdge(
                points.toFloatArray(),
                floatArrayOf(
                    (-sin(startAngle)).toFloat(), cos(startAngle).toFloat(),
                    (-sin(endAngle)).toFloat(), cos(endAngle).toFloat(),
                ),
            )
        }
        return contour
    }
}
