package ru.hollowhorizon.hollowengine.client.ui.widgets

import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.ui.BoxNode
import ru.hollowhorizon.hollowengine.client.ui.UiEvent
import ru.hollowhorizon.hollowengine.client.ui.UiEventKind
import ru.hollowhorizon.hollowengine.client.ui.UiTextAlign
import ru.hollowhorizon.hollowengine.client.ui.text.UiTextLayouter
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

    private fun TextFieldState.press(
        key: Int,
        ctrl: Boolean = false,
        shift: Boolean = false,
        alt: Boolean = false,
        layout: EditableFieldLayout? = null,
    ): Boolean {
        var mods = 0
        if (ctrl) mods = mods or GLFW.GLFW_MOD_CONTROL
        if (shift) mods = mods or GLFW.GLFW_MOD_SHIFT
        if (alt) mods = mods or GLFW.GLFW_MOD_ALT
        val input = UiKeyInput(UiEvent(UiEventKind.KEY_PRESSED, node = BoxNode(), key = key, modifiers = mods))
        return handleEditableFieldKey(this, input, layout)
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
    fun `up and down use visual lines when wrapped`() {
        val fontSize = 12f
        val text = "abcdefghij\nxy"
        val layout = computeEditableFieldLayout(
            text = text,
            fontSize = fontSize,
            fontFamily = null,
            wrap = true,
            viewportWidth = UiTextLayouter.measureTextWidth("abc", fontSize, null),
        )
        val firstLineVisuals = layout.lineLayouts[0]?.lines.orEmpty()
        assertTrue(firstLineVisuals.size > 1)

        val s = state(text, caret = firstLineVisuals.first().sourceStart)
        assertTrue(s.press(GLFW.GLFW_KEY_DOWN, layout = layout))
        assertEquals(firstLineVisuals[1].sourceStart, s.caret)

        s.setCarets(listOf(UiTextCaret(firstLineVisuals.last().sourceStart)))
        assertTrue(s.press(GLFW.GLFW_KEY_DOWN, layout = layout))
        assertEquals(11, s.caret)
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
    fun `left and right collapse selection to its matching boundary`() {
        state("abcdef").let {
            it.setSelection(anchor = 1, active = 4)
            assertTrue(it.press(GLFW.GLFW_KEY_LEFT))
            assertEquals(1, it.caret)
            assertFalse(it.hasSelection)
        }

        state("abcdef").let {
            it.setSelection(anchor = 4, active = 1)
            assertTrue(it.press(GLFW.GLFW_KEY_RIGHT))
            assertEquals(4, it.caret)
            assertFalse(it.hasSelection)
        }
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
    fun `ctrl-d duplicates selections`() {
        val s = state("foo bar")
        s.setCarets(listOf(UiTextCaret(3, 0), UiTextCaret(7, 4)))
        assertTrue(s.press(GLFW.GLFW_KEY_D, ctrl = true))
        assertEquals("foofoo barbar", s.text)
        assertEquals("foo\nbar", s.selectedText())
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

    @Test
    fun `double and triple click select a word or real line`() {
        val s = state("foo bar\nbaz")

        handleEditableFieldPress(s, offset = 1, clickCount = 2, modifiers = 0)
        assertEquals("foo", s.selectedText())

        handleEditableFieldPress(s, offset = 9, clickCount = 3, modifiers = 0)
        assertEquals("baz", s.selectedText())
    }

    @Test
    fun `alt double and triple click toggle added ranges`() {
        val s = state("foo bar\nbaz")
        val alt = GLFW.GLFW_MOD_ALT

        handleEditableFieldPress(s, offset = 1, clickCount = 1, modifiers = alt)
        assertEquals(listOf(s.text.length, 1), s.caretRanges.map { it.position })

        handleEditableFieldPress(s, offset = 1, clickCount = 2, modifiers = alt)
        assertEquals(listOf(s.text.length, 3), s.caretRanges.map { it.position })
        assertEquals("foo", s.selectedText())
        handleEditableFieldPress(s, offset = 1, clickCount = 2, modifiers = alt)
        assertFalse(s.hasSelection)

        handleEditableFieldPress(s, offset = 9, clickCount = 3, modifiers = alt)
        assertEquals("baz", s.selectedText())
        handleEditableFieldPress(s, offset = 9, clickCount = 3, modifiers = alt)
        assertFalse(s.hasSelection)
    }

    @Test
    fun `alt triple click replaces the word range from the second click`() {
        val s = state("foo bar\nbaz")
        val alt = GLFW.GLFW_MOD_ALT

        handleEditableFieldPress(s, offset = 1, clickCount = 1, modifiers = alt)
        handleEditableFieldPress(s, offset = 1, clickCount = 2, modifiers = alt)
        assertEquals("foo", s.selectedText())

        handleEditableFieldPress(s, offset = 1, clickCount = 3, modifiers = alt)
        assertEquals("foo bar", s.selectedText())
        assertEquals(listOf(s.text.length, 7), s.caretRanges.map { it.position })
    }

    @Test
    fun `alt drag updates the last caret range without dropping existing carets`() {
        val s = state("abcdef", caret = 0)
        val pointer = EditableFieldPointerState()
        pointer.beginPress(s.text, offset = 1, clickCount = 1, altPressed = true)

        handleEditableFieldPress(s, offset = 1, clickCount = 1, modifiers = GLFW.GLFW_MOD_ALT, pointerState = pointer)
        handleEditableFieldDrag(s, offset = 4, pointerState = pointer)

        assertEquals(listOf(0, 4), s.caretRanges.map { it.position })
        assertEquals("bcd", s.selectedText())

        handleEditableFieldPress(s, offset = 2, clickCount = 1, modifiers = GLFW.GLFW_MOD_ALT)
        assertFalse(s.hasSelection)
        assertEquals(listOf(0), s.caretRanges.map { it.position })
    }

    @Test
    fun `alt word drag expands the added range by whole words`() {
        val s = state("one two three", caret = 0)
        val pointer = EditableFieldPointerState()
        pointer.beginPress(s.text, offset = 1, clickCount = 2, altPressed = true)

        handleEditableFieldPress(s, offset = 1, clickCount = 2, modifiers = GLFW.GLFW_MOD_ALT, pointerState = pointer)
        handleEditableFieldDrag(s, offset = 6, pointerState = pointer)

        assertEquals("one two", s.selectedText())
    }

    @Test
    fun `wrapped empty line selection fills the row width`() {
        val fontSize = 12f
        val lineLayout = UiTextLayouter.layout(
            "",
            128f,
            Float.POSITIVE_INFINITY,
            wrap = true,
            align = UiTextAlign.LEFT,
            fontSize = fontSize,
            fontFamily = null,
            preserveWhitespace = true,
        )

        val rects = selectionRectsForRow(
            line = EditableFieldLine(start = 2, text = ""),
            lineLayout = lineLayout,
            localStart = 0,
            localEnd = 0,
            crossesNewline = true,
            fontSize = fontSize,
            fontFamily = null,
            fullWidth = 128f,
        )

        assertEquals(1, rects.size)
        assertEquals(0f, rects.single().x)
        assertEquals(128f, rects.single().width)
    }
}
