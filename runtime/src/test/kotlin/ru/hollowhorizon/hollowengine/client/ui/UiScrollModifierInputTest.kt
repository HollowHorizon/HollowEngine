package ru.hollowhorizon.hollowengine.client.ui

import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.ui.scroll.UiScrollHandle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class UiScrollModifierInputTest {
    @Test
    fun `scroll event exposes keyboard modifiers`() {
        var receivedModifiers = 0
        HollowUiSurface().use { surface ->
            surface.setContent {
                Box(
                    modifier = Modifier.size(100.px, 100.px)
                        .then(scrollModifier(horizontal = false))
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
                        .then(scrollModifier(vertical = false))
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

    @Test
    fun `a wheel handler on a plain node receives the notch`() {
        var received = 0
        HollowUiSurface().use { surface ->
            surface.setContent {
                Box(
                    modifier = Modifier.size(100.px, 100.px).onScroll { event ->
                        received++
                        event.consume()
                    },
                )
            }
            surface.frame(120f, 120f, 50f, 50f, 0L)

            val handled = surface.runtime.mouseScrolled(50f, 50f, 0f, 1f, GLFW.GLFW_MOD_CONTROL)

            assertEquals(1, received, "the handler must see the wheel without being a scroll container")
            assertTrue(handled)
        }
    }

    @Test
    fun `wheel prefers the highest overlapping scroll layer`() {
        val backScroll = UiScrollHandle()
        val frontScroll = UiScrollHandle()
        HollowUiSurface().use { surface ->
            surface.setContent {
                Box(mode = UiBoxMode.STACK, modifier = Modifier.size(100.px, 100.px)) {
                    Box(
                        id = "back-scroll",
                        modifier = Modifier.size(100.px, 100.px).then(scrollModifier(state = backScroll)).layer(0),
                    ) {
                        Box(modifier = Modifier.size(100.px, 240.px))
                    }
                    Box(
                        id = "front-scroll",
                        modifier = Modifier.size(100.px, 100.px).then(scrollModifier(state = frontScroll)).layer(10),
                    ) {
                        Box(modifier = Modifier.size(100.px, 240.px))
                    }
                }
            }
            val frame = surface.frame(120f, 120f, 50f, 50f, 0L)

            assertSame(frame.nodeByIdentifier("front-scroll"), frame.scrollTargetAt(50f, 50f))
        }
    }
}
