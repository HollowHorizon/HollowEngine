package ru.hollowhorizon.hollowengine.common.npcs.data

import ru.hollowhorizon.hollowengine.common.data.dataKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NpcDataStoreTest {
    private val visits = dataKey<Int>("hollowengine:visits") { 0 }
    private val name = dataKey<String>("hollowengine:name")

    @Test
    fun `typed values update and round trip`() {
        val data = NpcDataStore()

        assertEquals(0, data.get(visits))
        assertFalse(data.contains(visits))
        assertEquals(1, data.update(visits) { it + 1 })
        data.set(name, "Guide")

        val restored = NpcDataStore().apply { load(data.save()) }
        assertEquals(1, restored.get(visits))
        assertEquals("Guide", restored.get(name))
        assertTrue(restored.contains(visits))
    }

    @Test
    fun `removal restores a non-persistent default`() {
        val data = NpcDataStore()
        data.set(visits, 4)

        assertTrue(data.remove(visits))
        assertEquals(0, data.get(visits))
        assertFalse(data.contains(visits))
        assertNull(data.get(name))
    }
}
