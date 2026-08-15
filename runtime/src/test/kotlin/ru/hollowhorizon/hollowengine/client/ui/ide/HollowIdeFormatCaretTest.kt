package ru.hollowhorizon.hollowengine.client.ui.ide

import kotlin.test.Test
import kotlin.test.assertEquals

class HollowIdeFormatCaretTest {
    private val original = "fun f() {\nval x = 1\n}\n"
    private val formatted = "fun f() {\n    val x = 1\n}\n"

    @Test
    fun `caret keeps its place in the code after reindenting`() {
        val caret = original.indexOf("x = 1")

        val mapped = mapCaretThroughFormat(original, formatted, caret)

        assertEquals(formatted.indexOf("x = 1"), mapped)
    }

    @Test
    fun `caret inside the indentation lands on the first character of the line`() {
        val caret = original.indexOf("val x")

        val mapped = mapCaretThroughFormat(original, formatted, caret)

        assertEquals(formatted.indexOf("val x"), mapped)
    }

    @Test
    fun `caret on an untouched line stays where it was`() {
        val caret = original.indexOf("f()")

        assertEquals(caret, mapCaretThroughFormat(original, formatted, caret))
    }

    @Test
    fun `caret past the end of a shortened line clamps to it`() {
        val trailing = "fun f() {\nval x = 1    \n}\n"
        val caret = trailing.indexOf("\n}")

        val mapped = mapCaretThroughFormat(trailing, formatted, caret)

        assertEquals(formatted.indexOf("\n}"), mapped)
    }

    @Test
    fun `caret at the very end survives an added trailing newline`() {
        val withoutNewline = "val x = 1"
        val withNewline = "val x = 1\n"

        assertEquals(withoutNewline.length, mapCaretThroughFormat(withoutNewline, withNewline, withoutNewline.length))
    }

    @Test
    fun `caret sticks to the character on its left when inner spacing collapses`() {
        val spaced = "val x   =   1\n"
        val tight = "val x = 1\n"

        // Right after '=', which the collapse moves four characters to the left.
        val mapped = mapCaretThroughFormat(spaced, tight, spaced.indexOf('=') + 1)

        assertEquals(tight.indexOf('=') + 1, mapped)
    }

    @Test
    fun `caret in the middle of a word does not move within it`() {
        val spaced = "fun f() {\nval something   = 1\n}\n"
        val tight = "fun f() {\n    val something = 1\n}\n"
        val caret = spaced.indexOf("something") + 4

        val mapped = mapCaretThroughFormat(spaced, tight, caret)

        assertEquals(tight.indexOf("something") + 4, mapped)
    }

    @Test
    fun `caret on a blank line keeps its line`() {
        val before = "fun f() {\n   \nval x = 1\n}\n"
        val after = "fun f() {\n\n    val x = 1\n}\n"
        val caret = before.indexOf("   \n") + 2

        val mapped = mapCaretThroughFormat(before, after, caret)

        assertEquals(after.indexOf("\n\n") + 1, mapped)
    }
}
