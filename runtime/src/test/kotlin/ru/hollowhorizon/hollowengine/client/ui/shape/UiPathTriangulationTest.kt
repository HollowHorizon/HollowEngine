package ru.hollowhorizon.hollowengine.client.ui.shape

import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UiPathTriangulationTest {
    @Test
    fun `large convex contour triangulates as a fan`() {
        val points = List(256) { index ->
            val angle = index * PI * 2.0 / 256.0
            UiPathPoint((cos(angle) * 100.0).toFloat(), (sin(angle) * 100.0).toFloat())
        }

        val triangles = UiPathGeometry(listOf(UiPathContour(points, closed = true))).fillTriangles()

        assertEquals(254, triangles.size)
        assertTrue(triangles.all { triangleArea(it) > 0f })
    }

    @Test
    fun `concave contour still triangulates completely`() {
        val points = listOf(
            UiPathPoint(0f, 0f),
            UiPathPoint(4f, 0f),
            UiPathPoint(4f, 4f),
            UiPathPoint(2f, 2f),
            UiPathPoint(0f, 4f),
        )

        val triangles = UiPathGeometry(listOf(UiPathContour(points, closed = true))).fillTriangles()

        assertEquals(3, triangles.size)
        assertTrue(triangles.all { triangleArea(it) > 0f })
    }

    private fun triangleArea(triangle: UiPathTriangle): Float {
        val abX = triangle.second.x - triangle.first.x
        val abY = triangle.second.y - triangle.first.y
        val acX = triangle.third.x - triangle.first.x
        val acY = triangle.third.y - triangle.first.y
        return (abX * acY - abY * acX) * 0.5f
    }
}
