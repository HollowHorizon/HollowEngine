import kotlinx.serialization.Serializable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.common.attachments.api.ComponentStore
import ru.hollowhorizon.hollowengine.common.attachments.api.withoutLooseOnDeath
import ru.hollowhorizon.hollowengine.common.attachments.components.ComponentDescriptor
import ru.hollowhorizon.hollowengine.common.attachments.components.ComponentDescriptorRegistry
import ru.hollowhorizon.hollowengine.common.attachments.components.ComponentPersistencePolicy
import ru.hollowhorizon.hollowengine.common.utils.rl
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the component storage that replaced the immutable-snapshot indirection: writes have to land in
 * the backing map through every mutable path, including the ones that go via `keys` / `entries`.
 */
class ComponentStoreTests {
    private val keptId = "test:store_kept".rl
    private val looseId = "test:store_loose".rl

    @BeforeEach
    fun registerDescriptors() {
        if (ComponentDescriptorRegistry.descriptorOrNull(keptId) == null) {
            ComponentDescriptorRegistry.register(
                ComponentDescriptor(
                    id = keptId,
                    value = KeptComponent::class,
                    serializer = KeptComponent.serializer(),
                )
            )
        }
        if (ComponentDescriptorRegistry.descriptorOrNull(looseId) == null) {
            ComponentDescriptorRegistry.register(
                ComponentDescriptor(
                    id = looseId,
                    value = DroppedComponent::class,
                    serializer = DroppedComponent.serializer(),
                    persistencePolicy = ComponentPersistencePolicy.LOOSE_ON_DEATH,
                )
            )
        }
    }

    @AfterEach
    fun cleanup() {
        ComponentDescriptorRegistry.unregisterDescriptor(keptId)
        ComponentDescriptorRegistry.unregisterDescriptor(looseId)
    }

    @Test
    fun `a new store is empty and has no snapshot to write`() {
        val store = ComponentStore()

        assertTrue(store.isEmpty)
        assertTrue(store.copyOf().isEmpty())
    }

    @Test
    fun `writing through the mutable view reaches the backing map`() {
        val store = ComponentStore()

        store.asMutableMap()[keptId] = KeptComponent("hello")

        assertFalse(store.isEmpty)
        assertEquals(KeptComponent("hello"), store.readOnly[keptId])
    }

    @Test
    fun `overwriting a component returns the previous value and keeps one entry`() {
        val store = ComponentStore()
        val map = store.asMutableMap()
        map[keptId] = KeptComponent("first")

        val previous = map.put(keptId, KeptComponent("second"))

        assertEquals(KeptComponent("first"), previous)
        assertEquals(1, store.readOnly.size)
        assertEquals(KeptComponent("second"), store.readOnly[keptId])
    }

    @Test
    fun `removing through the view empties the store`() {
        val store = ComponentStore()
        val map = store.asMutableMap()
        map[keptId] = KeptComponent("gone")

        val removed = map.remove(keptId)

        assertEquals(KeptComponent("gone"), removed)
        assertTrue(store.isEmpty)
        assertNull(map.remove(keptId))
    }

    @Test
    fun `keys removeIf reaches the backing map`() {
        val store = ComponentStore()
        val map = store.asMutableMap()
        map[keptId] = KeptComponent("keep")
        map[looseId] = DroppedComponent("drop")

        map.keys.removeIf { it == looseId }

        assertEquals(setOf(keptId), store.readOnly.keys)
    }

    @Test
    fun `setValue through the entry set reaches the backing map`() {
        val store = ComponentStore()
        val map = store.asMutableMap()
        map[keptId] = KeptComponent("before")

        map.entries.single { it.key == keptId }.setValue(KeptComponent("after"))

        assertEquals(KeptComponent("after"), store.readOnly[keptId])
    }

    @Test
    fun `replaceAll swaps the whole set of components`() {
        val store = ComponentStore()
        store.asMutableMap()[keptId] = KeptComponent("old")

        store.replaceAll(listOf(DroppedComponent("new")))

        assertEquals(setOf(looseId), store.readOnly.keys)
    }

    @Test
    fun `withoutLooseOnDeath keeps only persistent components`() {
        val store = ComponentStore()
        store.replaceAll(listOf(KeptComponent("keep"), DroppedComponent("drop")))

        val survivors = store.copyOf().withoutLooseOnDeath()

        assertEquals(setOf(keptId), survivors.keys)
        assertEquals(KeptComponent("keep"), survivors[keptId])
    }

    @Test
    fun `copyOf does not alias the store`() {
        val store = ComponentStore()
        store.replaceAll(listOf(KeptComponent("original")))

        val copy = store.copyOf()
        store.asMutableMap()[keptId] = KeptComponent("changed")

        assertEquals(KeptComponent("original"), copy[keptId])
        assertEquals(KeptComponent("changed"), store.readOnly[keptId])
    }

    @Test
    fun `clear empties the store`() {
        val store = ComponentStore()
        store.replaceAll(listOf(KeptComponent("a"), DroppedComponent("b")))

        store.clear()

        assertTrue(store.isEmpty)
    }
}

@Serializable
private data class KeptComponent(val value: String)

@Serializable
private data class DroppedComponent(val value: String)
