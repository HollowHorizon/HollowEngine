package ru.hollowhorizon.hollowengine.common.logging

import org.apache.logging.log4j.spi.StandardLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HollowLogBufferTest {
    @Test
    fun `buffer retains newest entries and only snapshots changed revisions`() {
        val buffer = HollowLogBuffer(capacity = 2)
        buffer.append(StandardLevel.INFO, "first", "one", 1L)
        buffer.append(StandardLevel.WARN, "second", "two", 2L)
        buffer.append(StandardLevel.ERROR, "third", "three", 3L)

        val snapshot = buffer.snapshot()

        assertEquals(3L, snapshot.revision)
        assertEquals(listOf("two", "three"), snapshot.entries.map(HollowLogEntry::message))
        assertNull(buffer.snapshotAfter(snapshot.revision))
    }

    @Test
    fun `display message flattens every newline into one visual row`() {
        val entry = HollowLogEntry(
            id = 1L,
            level = StandardLevel.ERROR,
            loggerName = "test",
            message = "first\r\nsecond\nthird\rfourth",
            timeMillis = 0L,
        )

        assertEquals("first | second | third | fourth", entry.displayMessage)
    }
}
