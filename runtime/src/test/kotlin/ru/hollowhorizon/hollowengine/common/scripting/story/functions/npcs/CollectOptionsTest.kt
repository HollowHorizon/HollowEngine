package ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CollectOptionsTest {
    @Test
    fun `collection waits for items and returns by default`() {
        val options = CollectOptions()

        assertEquals(CollectSearchPolicy.WAIT_AND_RETRY, options.searchPolicy)
        assertEquals(10, options.searchIntervalTicks)
        assertTrue(options.returnToStart)
    }

    @Test
    fun `collection rejects invalid search interval`() {
        assertFailsWith<IllegalArgumentException> { CollectOptions(searchIntervalTicks = 0) }
    }
}
