package ru.hollowhorizon.hollowengine.client.ui.graph

import kotlin.test.Test
import kotlin.test.assertEquals

class GraphViewStateTest {
    @Test
    fun `a grabbed point stays under the pointer while a zoom is still easing in`() {
        val view = GraphViewState()
        view.zoomBy(2f, aroundX = 300f, aroundY = 200f)
        view.advance(1L)
        view.advance(16_000_001L)

        view.grab(100f, 100f)
        val grabbedX = view.toGraphX(100f)
        val grabbedY = view.toGraphY(100f)
        var frame = 16_000_001L
        repeat(20) { step ->
            view.dragTo(100f + step * 5f, 100f + step * 3f)
            frame += 16_000_000L
            view.advance(frame)
            assertEquals(grabbedX, view.toGraphX(100f + step * 5f), 1e-3f)
            assertEquals(grabbedY, view.toGraphY(100f + step * 3f), 1e-3f)
        }
    }
}
