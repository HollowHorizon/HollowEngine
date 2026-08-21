import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.common.attachments.api.Component
import ru.hollowhorizon.hollowengine.common.attachments.sync.ComponentSync
import ru.hollowhorizon.hollowengine.common.attachments.sync.EntityComponentSyncPacket
import ru.hollowhorizon.hollowengine.common.utils.rl
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The two rules that decide what goes over the wire and what is applied on arrival. Both used to be
 * absent: every change resent every component, and batches were applied in whatever order they landed.
 */
class ComponentSyncTests {
    private val model = "test:model".rl
    private val animator = "test:animator".rl

    private fun batch(
        current: Map<ResourceLocation, Component>,
        previous: Map<ResourceLocation, Component>,
    ) = ComponentSync.batchOf(current, previous)

    @Test
    fun `an unchanged component is not resent`() {
        val result = batch(
            current = mapOf(model to "player.gltf", animator to "wave"),
            previous = mapOf(model to "player.gltf", animator to "idle"),
        )

        assertEquals(setOf(animator), result.changed.keys)
        assertTrue(result.removed.isEmpty())
    }

    @Test
    fun `a dropped component is reported as removed`() {
        val result = batch(
            current = mapOf(model to "player.gltf"),
            previous = mapOf(model to "player.gltf", animator to "wave"),
        )

        assertTrue(result.changed.isEmpty())
        assertEquals(listOf(animator), result.removed)
    }

    @Test
    fun `a first batch carries everything`() {
        val result = batch(
            current = mapOf(model to "player.gltf", animator to "idle"),
            previous = emptyMap(),
        )

        assertEquals(setOf(model, animator), result.changed.keys)
        assertTrue(result.removed.isEmpty())
    }

    @Test
    fun `nothing to say produces an empty batch`() {
        val components = mapOf<ResourceLocation, Component>(model to "player.gltf")

        assertTrue(batch(components, components).isEmpty)
    }

    @Test
    fun `a delta must be strictly newer than what was applied`() {
        assertTrue(ComponentSync.shouldApply(version = 5, applied = 4, full = false))
        assertFalse(ComponentSync.shouldApply(version = 4, applied = 4, full = false))
        assertFalse(ComponentSync.shouldApply(version = 3, applied = 4, full = false))
    }

    @Test
    fun `a baseline applies at an equal version`() {
        assertTrue(ComponentSync.shouldApply(version = 0, applied = 0, full = true))
        assertTrue(ComponentSync.shouldApply(version = 7, applied = 7, full = true))
        assertTrue(ComponentSync.shouldApply(version = 8, applied = 7, full = true))
    }

    @Test
    fun `a stale baseline is still dropped`() {
        assertFalse(ComponentSync.shouldApply(version = 6, applied = 7, full = true))
    }

    private fun deferred() = ComponentSync.DeferredBatches(java.lang.ref.WeakReference(null))

    private fun delta(version: Long) = EntityComponentSyncPacket(entityId = 1, version = version)

    private fun baseline(version: Long) =
        EntityComponentSyncPacket(entityId = 1, version = version, full = true)

    @Test
    fun `parked batches come back in arrival order`() {
        val batches = deferred()
        batches.enqueue(delta(1))
        batches.enqueue(delta(2))
        batches.enqueue(delta(3))

        assertEquals(listOf(1L, 2L, 3L), batches.drain().map { it.version })
    }

    @Test
    fun `a parked baseline discards what was queued before it`() {
        val batches = deferred()
        batches.enqueue(delta(1))
        batches.enqueue(delta(2))
        batches.enqueue(baseline(3))
        batches.enqueue(delta(4))

        assertEquals(listOf(3L, 4L), batches.drain().map { it.version })
    }

    @Test
    fun `draining empties the queue so batches are never applied twice`() {
        val batches = deferred()
        batches.enqueue(delta(1))

        assertEquals(1, batches.drain().size)
        assertTrue(batches.drain().isEmpty())
        assertEquals(0, batches.size)
    }
}
