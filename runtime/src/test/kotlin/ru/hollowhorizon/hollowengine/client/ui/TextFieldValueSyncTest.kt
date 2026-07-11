package ru.hollowhorizon.hollowengine.client.ui

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextFieldValueSyncTest {
    @Test
    fun `delayed controlled value echoes are not treated as external edits`() {
        val sync = TextFieldValueSync("")
        sync.recordNotification("a")
        sync.recordNotification("aa")
        sync.recordNotification("aaa")

        assertTrue(sync.acknowledge("a"))
        assertTrue(sync.acknowledge("aa"))
        assertTrue(sync.acknowledge("aaa"))
        assertFalse(sync.acknowledge("external"))
    }
}
