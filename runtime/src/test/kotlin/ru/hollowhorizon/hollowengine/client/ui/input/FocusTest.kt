package ru.hollowhorizon.hollowengine.client.ui.input

import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.ui.*
import kotlin.test.assertEquals

class FocusTest {
    private fun keyLoggingBox(id: String, layer: Int, log: MutableList<String>, consume: Boolean): BoxNode {
        // A focus scope is always key-active while composed, so it is a self-contained multi-focus target.
        val mod = Modifier.focusScope().layer(layer).onKeyInput { input ->
            log += id
            if (consume) input.consume()
        }
        return BoxNode(id = id, modifiers = listOf(mod))
    }

    @Test
    fun `keys reach every focus-active node, highest layer first`() {
        val log = mutableListOf<String>()
        val top = keyLoggingBox("top", layer = 10, log, consume = false)
        val bottom = keyLoggingBox("bottom", layer = 0, log, consume = false)
        val root = BoxNode(measurePolicy = UiMeasurePolicies.Column)
            .also { it.children.add(top); it.children.add(bottom) }
        val runtime = HollowUiRuntime()
        runtime.frame(root, 200f, 200f, -1f, -1f, 0L)

        runtime.keyPressed(GLFW.GLFW_KEY_A, 0, 0)
        assertEquals(listOf("top", "bottom"), log, "both focus-active nodes get the key, top layer first")
    }

    @Test
    fun `a consuming focus-active node stops lower-priority ones`() {
        val log = mutableListOf<String>()
        val top = keyLoggingBox("top", layer = 10, log, consume = true)
        val bottom = keyLoggingBox("bottom", layer = 0, log, consume = false)
        val root = BoxNode(measurePolicy = UiMeasurePolicies.Column)
            .also { it.children.add(top); it.children.add(bottom) }
        val runtime = HollowUiRuntime()
        runtime.frame(root, 200f, 200f, -1f, -1f, 0L)

        runtime.keyPressed(GLFW.GLFW_KEY_A, 0, 0)
        assertEquals(listOf("top"), log, "top consumes, bottom never sees the key")
    }
}
