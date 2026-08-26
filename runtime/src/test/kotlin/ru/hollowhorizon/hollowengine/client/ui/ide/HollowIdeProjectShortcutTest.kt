package ru.hollowhorizon.hollowengine.client.ui.ide

import org.lwjgl.glfw.GLFW
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HollowIdeProjectShortcutTest {
    @Test
    fun `paste shortcut works for the project root`() {
        assertEquals(
            ProjectShortcut.Paste,
            resolveProjectShortcut(GLFW.GLFW_KEY_V, GLFW.GLFW_MOD_CONTROL, hasSelectedPath = false),
        )
    }

    @Test
    fun `shortcuts requiring a concrete path stay disabled for the project root`() {
        assertNull(resolveProjectShortcut(GLFW.GLFW_KEY_C, GLFW.GLFW_MOD_CONTROL, hasSelectedPath = false))
        assertNull(resolveProjectShortcut(GLFW.GLFW_KEY_X, GLFW.GLFW_MOD_CONTROL, hasSelectedPath = false))
        assertNull(resolveProjectShortcut(GLFW.GLFW_KEY_DELETE, 0, hasSelectedPath = false))
        assertNull(resolveProjectShortcut(GLFW.GLFW_KEY_F2, 0, hasSelectedPath = false))
    }

    @Test
    fun `create shortcuts work for the project root`() {
        assertEquals(
            ProjectShortcut.CreateFile,
            resolveProjectShortcut(GLFW.GLFW_KEY_INSERT, GLFW.GLFW_MOD_ALT, hasSelectedPath = false),
        )
        assertEquals(
            ProjectShortcut.CreateFolder,
            resolveProjectShortcut(
                GLFW.GLFW_KEY_INSERT,
                GLFW.GLFW_MOD_ALT or GLFW.GLFW_MOD_SHIFT,
                hasSelectedPath = false,
            ),
        )
    }
}
