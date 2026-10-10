package ru.hollowhorizon.hollowengine.common.scripting.nodes

import kotlinx.serialization.json.JsonPrimitive
import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.common.attachments.editor.ScriptField
import ru.hollowhorizon.hollowengine.common.attachments.editor.ScriptSchema
import ru.hollowhorizon.hollowengine.common.data.DataKey
import ru.hollowhorizon.hollowengine.common.data.dataKey
import ru.hollowhorizon.hollowengine.common.data.read
import ru.hollowhorizon.hollowengine.common.data.write
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NodePropertyTest {
    @Test
    fun `saved value is available when the script initializes dependent properties`() {
        val key = dataKey<Int>("radius")
        val saved = CompoundTag().apply { write(key, 12) }
        var defaults = 0
        val radius by PersistedValue(key, { defaults++; 3 }, saved)
        val diameter = radius * 2

        assertEquals(24, diameter)
        assertEquals(0, defaults)
    }

    @Test
    fun `missing value evaluates its default once and follows changed script defaults on reload`() {
        val key = dataKey<Int>("radius")
        var defaults = 0
        val storage = PersistedValue(key, { defaults++; 3 }, CompoundTag())
        assertEquals(3, storage.current())
        assertEquals(3, storage.current())
        assertEquals(1, defaults)

        val saved = CompoundTag().also(storage::save)
        assertTrue(saved.isEmpty)
        assertEquals(5, PersistedValue(key, { 5 }, saved).current())
    }

    @Test
    fun `constructor assignments are persisted after restoring the initial value`() {
        val key = dataKey<Int>("count")
        val saved = CompoundTag().apply { write(key, 4) }
        val storage = PersistedValue(key, { 0 }, saved)
        var count by storage
        count += 1

        val nextSave = CompoundTag().also(storage::save)
        assertEquals(5, nextSave.read(key))
        assertEquals(4, saved.read(key))
    }

    @Test
    fun `restart property restores its edited value before dependent initialization`() {
        val key = dataKey<String>("animation")
        val changes = mutableListOf<String>()
        val original = editorProperty(key, PersistedValue(key, { "idle" }), true, changes::add)
        assertTrue(original.apply(JsonPrimitive("walk")))
        assertFalse(original.apply(JsonPrimitive("walk")))

        val saved = CompoundTag().also(original::save)
        val restored = editorProperty(key, PersistedValue(key, { "idle" }, saved), true, changes::add)
        val animation by restored
        val configuredAnimation = "clip:$animation"

        assertEquals("clip:walk", configuredAnimation)
        assertEquals(listOf("walk"), changes)
        assertEquals(JsonPrimitive("walk"), restored.encode())
    }

    @Test
    fun `live property changes without requesting restart and rejects invalid values`() {
        val key = dataKey<Int>("radius")
        val changes = mutableListOf<Int>()
        val property = editorProperty(key, PersistedValue(key, { 3 }), false, changes::add)
        val radius by property

        assertFalse(property.apply(JsonPrimitive(8)))
        assertEquals(8, radius)
        assertFalse(property.apply(JsonPrimitive("invalid")))
        assertEquals(8, radius)
        assertEquals(listOf(8), changes)
    }

    @Test
    fun `structured persistent values survive initialization and another save`() {
        val key = dataKey<List<String>>("targets")
        val saved = CompoundTag().apply { write(key, listOf("first", "second")) }
        val storage = PersistedValue(key, { emptyList() }, saved)
        val targets by storage
        val selected = targets.joinToString(",")

        assertEquals("first,second", selected)
        assertEquals(listOf("first", "second"), CompoundTag().also(storage::save).read(key))
    }

    private fun <T : Any> editorProperty(
        key: DataKey<T>,
        storage: PersistedValue<T>,
        restart: Boolean,
        onChange: (T) -> Unit,
    ) = EditorProperty(
        ScriptField(key.name, ScriptSchema.typeOf(key.serializer.descriptor)), key, storage, restart, onChange,
    )
}
