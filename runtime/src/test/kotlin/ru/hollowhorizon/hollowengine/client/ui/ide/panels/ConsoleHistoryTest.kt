package ru.hollowhorizon.hollowengine.client.ui.ide.panels

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConsoleHistoryTest {
    private class ListStore(override val entries: MutableList<String>) : ConsoleHistoryStore {
        override fun add(entry: String) {
            entries += entry
        }
    }

    @Test
    fun `stepping past the newest entry brings back the text being typed`() {
        val history = ConsoleHistory(ListStore(mutableListOf("time set day", "weather clear")))

        assertEquals("weather clear", history.step("give @s", older = true))
        assertEquals("time set day", history.step("weather clear", older = true))
        assertNull(history.step("time set day", older = true))
        assertEquals("weather clear", history.step("time set day", older = false))
        assertEquals("give @s", history.step("weather clear", older = false))
        assertNull(history.step("give @s", older = false))
    }
}
