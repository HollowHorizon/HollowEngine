package ru.hollowhorizon.hollowengine.client.ui.ide

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HollowIdeFindReplaceTest {
    private fun state(
        query: String,
        matchCase: Boolean = false,
        wholeWord: Boolean = false,
        regex: Boolean = false,
        replacement: String = "",
    ) = HollowIdeFindState("file").apply {
        open(replace = replacement.isNotEmpty(), seed = null)
        this.query = query
        this.matchCase = matchCase
        this.wholeWord = wholeWord
        this.regex = regex
        this.replacement = replacement
    }

    @Test
    fun `a closed bar matches nothing`() {
        val state = state("value").apply { close() }
        assertTrue(state.matches("value value").isEmpty())
    }

    @Test
    fun `plain search ignores case by default and honours the toggle`() {
        assertEquals(2, state("value").matches("Value value").size)
        assertEquals(1, state("value", matchCase = true).matches("Value value").size)
    }

    @Test
    fun `whole word skips matches inside longer identifiers`() {
        val matches = state("value", wholeWord = true).matches("value valueOf myValue value_1")
        assertEquals(listOf(0..4), matches)
    }

    @Test
    fun `a plain query with regex characters is taken literally`() {
        assertEquals(listOf(4..6), state("a.c").matches("abc a.c"))
    }

    @Test
    fun `an unparseable regex matches nothing instead of throwing`() {
        assertTrue(state("(unclosed", regex = true).matches("(unclosed)").isEmpty())
    }

    @Test
    fun `regex replacement expands group references`() {
        val state = state("(\\w+)=(\\w+)", regex = true, replacement = "$2=$1")
        val text = "size=big"
        val match = state.matches(text).single()
        assertEquals("big=size", state.expandReplacement(text, match))
    }

    @Test
    fun `a literal replacement is never treated as a group reference`() {
        val state = state("x", replacement = "$1")
        assertEquals("$1", state.expandReplacement("x", state.matches("x").single()))
    }

    @Test
    fun `the first match at or after the caret is selected when the bar opens`() {
        val state = state("it")
        val matches = state.matches("it is it")
        assertEquals(1, state.indexFrom(matches, offset = 3))
        assertEquals(0, state.indexFrom(matches, offset = 0))
        // Past the last match the search wraps back to the top.
        assertEquals(0, state.indexFrom(matches, offset = 99))
    }

    @Test
    fun `matches are recomputed when the query changes`() {
        val state = state("a")
        assertEquals(2, state.matches("a b a").size)
        state.query = "b"
        assertEquals(1, state.matches("a b a").size)
    }
}
