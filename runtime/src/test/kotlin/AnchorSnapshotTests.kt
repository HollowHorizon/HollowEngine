import kotlinx.serialization.KSerializer
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.common.geary.anchor.EntityAnchor
import ru.hollowhorizon.hollowengine.common.geary.anchor.PrimaryAnchorObject
import ru.hollowhorizon.hollowengine.common.geary.anchor.StableKeyComponent
import ru.hollowhorizon.hollowengine.common.geary.anchor.WorldAnchor
import ru.hollowhorizon.hollowengine.common.geary.anchor.anchorOrNull
import ru.hollowhorizon.hollowengine.common.geary.anchor.primaryAnchorOrNull
import ru.hollowhorizon.hollowengine.common.geary.anchor.requireStableKey
import ru.hollowhorizon.hollowengine.common.geary.anchor.withIdentity
import ru.hollowhorizon.hollowengine.common.geary.anchor.worldAnchorFor
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentDescriptor
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentDescriptorRegistry
import ru.hollowhorizon.hollowengine.common.geary.components.TransformComponent
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySerialization
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySnapshot
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class AnchorSnapshotTests {
    private val registeredForTest = mutableSetOf<ResourceLocation>()

    @AfterEach
    fun cleanup() {
        registeredForTest.forEach(ComponentDescriptorRegistry::unregisterDescriptor)
        registeredForTest.clear()
    }

    @Test
    fun `anchor snapshot roundtrip preserves stable key and world anchor`() {
        registerAnchorDescriptors()
        val stableKey = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        val localId = UUID.fromString("123e4567-e89b-12d3-a456-426614174001")
        val snapshot = EntitySnapshot(
            components = listOf(
                StableKeyComponent(stableKey),
                WorldAnchor(chunkX = 4, chunkZ = -2, localId = localId),
                TransformComponent(x = 12f, y = 64f, z = -5f, yaw = 45f, pitch = 10f, scale = 1.5f),
            )
        )

        val yamlDecoded = EntitySerialization.deserializeFromYaml(EntitySerialization.serializeToYaml(snapshot))
        val nbtDecoded = EntitySerialization.deserializeFromNbt(EntitySerialization.serializeToNbt(snapshot))

        assertEquals(stableKey, yamlDecoded.requireStableKey())
        assertEquals(stableKey, nbtDecoded.requireStableKey())
        assertEquals(snapshot, yamlDecoded)
        assertEquals(snapshot, nbtDecoded)
    }

    @Test
    fun `with identity replaces previous stable key and anchor components`() {
        registerAnchorDescriptors()
        val oldStableKey = UUID.fromString("123e4567-e89b-12d3-a456-426614174010")
        val newStableKey = UUID.fromString("123e4567-e89b-12d3-a456-426614174011")
        val snapshot = EntitySnapshot(
            components = listOf(
                StableKeyComponent(oldStableKey),
                EntityAnchor(oldStableKey, primary = true),
                PrimaryAnchorObject(),
                TransformComponent(x = 1f, y = 2f, z = 3f),
            )
        )

        val updated = snapshot.withIdentity(
            WorldAnchor(chunkX = 8, chunkZ = 16, localId = UUID.fromString("123e4567-e89b-12d3-a456-426614174012")),
            newStableKey,
        )

        assertEquals(newStableKey, updated.requireStableKey())
        assertIs<WorldAnchor>(updated.anchorOrNull())
        assertNull(updated.primaryAnchorOrNull())
        assertFalse(updated.components.any { it is EntityAnchor })
    }

    @Test
    fun `world anchor uses chunk coordinates from world position`() {
        val anchor = worldAnchorFor(Vec3(31.9, 70.0, -32.1), UUID.fromString("123e4567-e89b-12d3-a456-426614174020"))

        assertEquals(1, anchor.chunkX)
        assertEquals(-3, anchor.chunkZ)
    }

    private fun registerAnchorDescriptors() {
        ensureRegistered(ResourceLocation("hollowengine", "stable_key"), StableKeyComponent::class, StableKeyComponent.serializer())
        ensureRegistered(ResourceLocation("hollowengine", "anchor/entity"), EntityAnchor::class, EntityAnchor.serializer())
        ensureRegistered(ResourceLocation("hollowengine", "anchor/world"), WorldAnchor::class, WorldAnchor.serializer())
        ensureRegistered(ResourceLocation("hollowengine", "anchor/primary"), PrimaryAnchorObject::class, PrimaryAnchorObject.serializer())
        ensureRegistered(ResourceLocation("hollowengine", "transform"), TransformComponent::class, TransformComponent.serializer())
    }

    private fun <T : Any> ensureRegistered(id: ResourceLocation, type: KClass<T>, serializer: KSerializer<T>) {
        if (ComponentDescriptorRegistry.descriptorOrNull(id) != null) return
        ComponentDescriptorRegistry.register(
            ComponentDescriptor(
                id = id,
                value = type,
                serializer = serializer,
            )
        )
        registeredForTest += id
    }
}
