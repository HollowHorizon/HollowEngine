package ru.hollowhorizon.hollowengine.client.ui

import org.lwjgl.glfw.GLFW
import kotlin.test.Test
import kotlin.test.assertEquals

class UiScrollModifierInputTest {
    @Test
    fun `scroll event exposes keyboard modifiers`() {
        var receivedModifiers = 0
        HollowUiSurface().use { surface ->
            surface.setContent {
                Box(
                    modifier = Modifier.size(100.px, 100.px)
                        .scroll(vertical = true)
                        .onScroll { event ->
                            receivedModifiers = event.modifiers
                            event.consume()
                        },
                ) {
                    Box(modifier = Modifier.size(100.px, 200.px))
                }
            }
            surface.frame(120f, 120f, 50f, 50f, 0L)

            surface.runtime.mouseScrolled(50f, 50f, 0f, 1f, GLFW.GLFW_MOD_CONTROL)

            assertEquals(GLFW.GLFW_MOD_CONTROL, receivedModifiers)
        }
    }

    @Test
    fun `raw wheel delta survives horizontal overflow routing`() {
        var routedScrollY = Float.NaN
        var rawScrollY = Float.NaN
        HollowUiSurface().use { surface ->
            surface.setContent {
                Box(
                    modifier = Modifier.size(100.px, 100.px)
                        .scroll(horizontal = true)
                        .onScroll { event ->
                            routedScrollY = event.scrollY
                            rawScrollY = event.rawScrollY
                            event.consume()
                        },
                ) {
                    Box(modifier = Modifier.size(200.px, 100.px))
                }
            }
            surface.frame(120f, 120f, 50f, 50f, 0L)

            surface.runtime.mouseScrolled(50f, 50f, 0f, 1f, GLFW.GLFW_MOD_CONTROL)

            assertEquals(0f, routedScrollY)
            assertEquals(1f, rawScrollY)
        }
    }
}
