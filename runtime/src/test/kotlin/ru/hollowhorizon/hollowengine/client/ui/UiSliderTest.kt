package ru.hollowhorizon.hollowengine.client.ui

import kotlin.test.Test
import kotlin.test.assertTrue

class UiSliderTest {
    @Test
    fun `thumb stays inside slider at both range boundaries`() {
        assertThumbInside(value = 0f)
        assertThumbInside(value = 1f)
    }

    private fun assertThumbInside(value: Float) {
        HollowUiSurface().use { surface ->
            surface.setContent {
                Slider(value = value, id = "slider", modifier = Modifier.size(120.px, 16.px))
            }
            val frame = surface.frame(140f, 36f, -1f, -1f, 0L)
            val slider = frame.nodes.single { it.id == "slider" }
            val track = frame.nodes.single { "slider-track" in it.tags }
            val thumb = frame.nodes.single { "slider-thumb" in it.tags }
            val sliderRect = frame.layout[slider].rect
            val trackRect = frame.layout[track].rect
            val thumbRect = frame.layout[thumb].rect

            assertTrue(trackRect.width >= sliderRect.width - 12.01f)
            assertTrue(thumbRect.x >= sliderRect.x)
            assertTrue(thumbRect.x + thumbRect.width <= sliderRect.x + sliderRect.width)
        }
    }
}
