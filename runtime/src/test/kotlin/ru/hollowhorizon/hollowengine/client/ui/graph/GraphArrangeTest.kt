package ru.hollowhorizon.hollowengine.client.ui.graph

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GraphArrangeTest {
    private val boxes = mapOf(
        "a" to GraphRect(0f, 0f, 100f, 40f),
        "b" to GraphRect(150f, 30f, 60f, 80f),
        "c" to GraphRect(400f, 10f, 100f, 20f),
    )

    @Test
    fun `aligning lines nodes up on the edge of all of them and leaves out those already there`() {
        assertEquals(mapOf("b" to (150f to 0f), "c" to (400f to 0f)), GraphArrange.align(boxes, GraphAlignment.TOP))
        // Bottoms go to 110, the lowest edge; b already ends there.
        assertEquals(mapOf("a" to (0f to 70f), "c" to (400f to 90f)), GraphArrange.align(boxes, GraphAlignment.BOTTOM))
    }

    @Test
    fun `spacing out gives nodes of different widths equal gaps and keeps the outer ones`() {
        // From 0 to 500 there are 260 units of nodes, so each of the two gaps is 120.
        val moved = GraphArrange.distribute(boxes, horizontal = true)

        assertEquals(mapOf("b" to (220f to 30f)), moved)
        assertTrue(GraphArrange.distribute(boxes - "c", horizontal = true).isEmpty())
    }
}
