package ru.hollowhorizon.hollowengine.client.ui.style

import ru.hollowhorizon.hollowengine.client.ui.UiColor
import kotlin.test.Test
import kotlin.test.assertEquals

class UiColorInterpolationTest {
    private val gray = UiColor(0.2f, 0.21f, 0.26f, 1f)

    @Test
    fun `fading in from transparent keeps the target color instead of passing through black`() {
        val halfway = UiColor.Transparent.interpolate(gray, 0.5f)

        assertEquals(gray.red, halfway.red, 1e-5f)
        assertEquals(gray.green, halfway.green, 1e-5f)
        assertEquals(gray.blue, halfway.blue, 1e-5f)
        assertEquals(0.5f, halfway.alpha, 1e-5f)
    }

    @Test
    fun `opaque colors mix exactly as before`() {
        val white = UiColor.White
        val mixed = gray.interpolate(white, 0.25f)

        assertEquals(gray.red + (1f - gray.red) * 0.25f, mixed.red, 1e-5f)
        assertEquals(1f, mixed.alpha, 1e-5f)
    }

    @Test
    fun `mixing two transparent colors does not divide by zero`() {
        val mixed = UiColor.Transparent.interpolate(UiColor(1f, 1f, 1f, 0f), 0.5f)

        assertEquals(0f, mixed.alpha)
        assertEquals(0.5f, mixed.red, 1e-5f)
    }
}
