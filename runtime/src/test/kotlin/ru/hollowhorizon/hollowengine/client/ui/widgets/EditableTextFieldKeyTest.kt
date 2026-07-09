package ru.hollowhorizon.hollowengine.client.ui.widgets

import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.ui.BoxNode
import ru.hollowhorizon.hollowengine.client.ui.UiEvent
import ru.hollowhorizon.hollowengine.client.ui.UiEventKind
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The field keymap routes key presses into [TextFieldState] edits/navigation (with Ctrl variants).
 * The state ops themselves are covered by TextFieldStateTest; this pins the wiring.
 */
class EditableTextFieldKeyTest {
    private fun state(text: String, caret: Int = text.length) =
        TextFieldState(initialText = text, initialCaret = caret, multiline = true, indentSize = 4, autoPairs = false)

    private fun TextFieldState.press(key: Int, ctrl: Boolean = false, shift: Boolean = false, alt: Boolean = false): Boolean {
        var mods = 0
        if (ctrl) mods = mods or GLFW.GLFW_MOD_CONTROL
        if (shift) mods = mods or GLFW.GLFW_MOD_SHIFT
        if (alt) mods = mods or GLFW.GLFW_MOD_ALT
        val input = UiKeyInput(UiEvent(UiEventKind.KEY_PRESSED, node = BoxNode(), key = key, modifiers = mods))
        return handleEditableFieldKey(this, input)
    }

    @Test
    fun `backspace and ctrl-backspace`() {
        state("foo bar").let { assertTrue(it.press(GLFW.GLFW_KEY_BACKSPACE)); assertEquals("foo ba", it.text) }
        state("foo bar").let { assertTrue(it.press(GLFW.GLFW_KEY_BACKSPACE, ctrl = true)); assertEquals("foo ", it.text) }
    }

    @Test
    fun `delete forward`() {
        state("abc", caret = 0).let { assertTrue(it.press(GLFW.GLFW_KEY_DELETE)); assertEquals("bc", it.text) }
    }

    @Test
    fun `arrows move the caret and ctrl jumps words`() {
        state("foo bar", caret = 7).let { it.press(GLFW.GLFW_KEY_LEFT); assertEquals(6, it.caret) }
        state("foo bar", caret = 7).let { it.press(GLFW.GLFW_KEY_LEFT, ctrl = true); assertEquals(4, it.caret) }
        state("foo bar", caret = 0).let { it.press(GLFW.GLFW_KEY_RIGHT); assertEquals(1, it.caret) }
    }

    @Test
    fun `up and down keep the column across lines`() {
        state("abc\nxy\nlonger", caret = 10).let {
            // caret at column 3 of "longer" -> "xy" clamps to its length
            it.press(GLFW.GLFW_KEY_UP)
            assertEquals(6, it.caret, "clamped to end of 'xy'")
        }
        state("abc\nxy", caret = 2).let {
            it.press(GLFW.GLFW_KEY_DOWN)
            assertEquals(6, it.caret, "column 2 on 'xy'")
        }
    }

    @Test
    fun `ctrl-alt-down adds a caret on the next line`() {
        val s = state("ab\ncd", caret = 1)
        assertTrue(s.press(GLFW.GLFW_KEY_DOWN, ctrl = true, alt = true))
        assertEquals(listOf(1, 4), s.caretRanges.map { it.position })
    }

    @Test
    fun `home and end move within the line`() {
        state("ab\ncd", caret = 4).let { it.press(GLFW.GLFW_KEY_HOME); assertEquals(3, it.caret) }
        state("ab\ncd", caret = 3).let { it.press(GLFW.GLFW_KEY_END); assertEquals(5, it.caret) }
    }

    @Test
    fun `shift-arrow extends a selection`() {
        val s = state("abc", caret = 0)
        s.press(GLFW.GLFW_KEY_RIGHT, shift = true)
        s.press(GLFW.GLFW_KEY_RIGHT, shift = true)
        assertEquals("ab", s.selectedText())
    }

    @Test
    fun `enter inserts a newline keeping indent`() {
        val s = state("    foo")
        assertTrue(s.press(GLFW.GLFW_KEY_ENTER))
        assertEquals("    foo\n    ", s.text)
    }

    @Test
    fun `tab indents and shift-tab unindents`() {
        state("foo", caret = 0).let { assertTrue(it.press(GLFW.GLFW_KEY_TAB)); assertEquals("    foo", it.text) }
        val s = state("        foo")
        s.setSelection(0, s.text.length)
        assertTrue(s.press(GLFW.GLFW_KEY_TAB, shift = true))
        assertEquals("    foo", s.text)
    }

    @Test
    fun `ctrl-a selects all and ctrl-z undoes`() {
        val s = state("hello")
        assertTrue(s.press(GLFW.GLFW_KEY_A, ctrl = true))
        assertEquals("hello", s.selectedText())

        val edited = state("ab")
        edited.insert("c")
        assertEquals("abc", edited.text)
        assertTrue(edited.press(GLFW.GLFW_KEY_Z, ctrl = true))
        assertEquals("ab", edited.text)
    }

    @Test
    fun `an unhandled key is reported as not handled`() {
        assertFalse(state("x").press(GLFW.GLFW_KEY_F5))
    }

    @Test
    fun `vertical move on the first and last lines pins to the document edges`() {
        assertEquals(0, verticalCaretMove("ab\ncd", 1, -1))
        assertEquals(5, verticalCaretMove("ab\ncd", 4, 1))
    }

    @Test
    fun `line splitting maps offsets`() {
        val lines = editableFieldLines("ab\n\ncd")
        assertEquals(listOf(0, 3, 4), lines.map { it.start })
        assertEquals(listOf("ab", "", "cd"), lines.map { it.text })
    }
}
