import com.mineinabyss.geary.prefabs.PrefabKey
import kotlinx.serialization.Serializable
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentDescriptor
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentDescriptorRegistry
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentPersistencePolicy
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySerialization
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySnapshot
import ru.hollowhorizon.hollowengine.common.prefabs.PrefabSystem
import java.io.File
import java.lang.reflect.Proxy
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EntitySerializationTests {
    private val basicId = ResourceLocation("test", "basic_component")
    private val looseId = ResourceLocation("test", "loose_component")
    private val testPrefabDir = DirectoryManager.HOLLOW_ENGINE.resolve("prefabs/tests/entity-serialization").toFile()

    @AfterEach
    fun cleanup() {
        ComponentDescriptorRegistry.unregisterDescriptor(basicId)
        ComponentDescriptorRegistry.unregisterDescriptor(looseId)
        if (testPrefabDir.exists()) testPrefabDir.deleteRecursively()
        PrefabSystem.onResourceManagerReload(dummyResourceManager())
    }

    @Test
    fun `snapshot roundtrip preserves components and prefab refs across yaml and nbt`() {
        registerDescriptors()
        val snapshot = EntitySnapshot(
            prefabRefs = setOf(PrefabKey.of("test:base"), PrefabKey.of("test:visuals")),
            components = listOf(BasicComponent("hello"), LooseComponent("temp")),
        )

        val yamlDecoded = EntitySerialization.deserializeFromYaml(EntitySerialization.serializeToYaml(snapshot))
        val nbtDecoded = EntitySerialization.deserializeFromNbt(EntitySerialization.serializeToNbt(snapshot))

        assertEquals(snapshot, yamlDecoded)
        assertEquals(snapshot, nbtDecoded)
    }

    @Test
    fun `drop loose on death removes only marked components`() {
        registerDescriptors()
        val snapshot = EntitySnapshot(
            components = listOf(BasicComponent("keep"), LooseComponent("drop")),
        )

        val filtered = snapshot.dropLooseOnDeathComponents()

        assertEquals(listOf(BasicComponent("keep")), filtered.components)
    }

    @Test
    fun `prefab resolver merges inherited components and keeps deterministic overrides`() {
        registerDescriptors()
        testPrefabDir.mkdirs()

        writePrefab(
            File(testPrefabDir, "base.entity.prefab"),
            EntitySnapshot(
                components = listOf(BasicComponent("base"), LooseComponent("base-loose")),
            )
        )
        writePrefab(
            File(testPrefabDir, "derived.entity.prefab"),
            EntitySnapshot(
                prefabRefs = setOf(PrefabKey.of("hollowengine:tests/entity-serialization/base")),
                components = listOf(BasicComponent("derived")),
            )
        )

        PrefabSystem.onResourceManagerReload(dummyResourceManager())
        val resolved = PrefabSystem.resolve("prefabs/tests/entity-serialization/derived.entity.prefab")

        assertEquals(BasicComponent("derived"), resolved.components.filterIsInstance<BasicComponent>().single())
        assertEquals(LooseComponent("base-loose"), resolved.components.filterIsInstance<LooseComponent>().single())
        assertEquals(setOf(PrefabKey.of("hollowengine:tests/entity-serialization/base")), resolved.prefabRefs)
    }

    @Test
    fun `unknown component in snapshot yaml fails fast`() {
        registerDescriptors()
        val yaml = """
            version: 2
            prefabRefs: []
            components:
              - type: test:missing_component
                value: nope
        """.trimIndent()

        assertFailsWith<Throwable> {
            EntitySerialization.deserializeFromYaml(yaml)
        }
    }

    private fun registerDescriptors() {
        if (ComponentDescriptorRegistry.descriptorOrNull(basicId) == null) {
            ComponentDescriptorRegistry.register(
                ComponentDescriptor(
                    id = basicId,
                    value = BasicComponent::class,
                    serializer = BasicComponent.serializer(),
                )
            )
        }
        if (ComponentDescriptorRegistry.descriptorOrNull(looseId) == null) {
            ComponentDescriptorRegistry.register(
                ComponentDescriptor(
                    id = looseId,
                    value = LooseComponent::class,
                    serializer = LooseComponent.serializer(),
                    persistencePolicy = ComponentPersistencePolicy.LOOSE_ON_DEATH,
                )
            )
        }
    }

    private fun writePrefab(file: File, snapshot: EntitySnapshot) {
        file.parentFile.mkdirs()
        file.writeText(EntitySerialization.serializeToYaml(snapshot))
    }

    private fun dummyResourceManager(): ResourceManager {
        return Proxy.newProxyInstance(
            ResourceManager::class.java.classLoader,
            arrayOf(ResourceManager::class.java),
        ) { _, _, _ ->
            throw UnsupportedOperationException("Not used in tests")
        } as ResourceManager
    }
}

@Serializable
private data class BasicComponent(val value: String)

@Serializable
private data class LooseComponent(val value: String)
