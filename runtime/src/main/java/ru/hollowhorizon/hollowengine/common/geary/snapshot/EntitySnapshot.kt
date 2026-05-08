package ru.hollowhorizon.hollowengine.common.geary.snapshot

import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.geary.api.Component
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentDescriptorRegistry
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentPersistencePolicy
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForResourceLocation
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForUuid
import java.util.*

@Serializable
sealed class Snapshot {
    abstract val components: List<@Polymorphic Component>

    fun componentById(): LinkedHashMap<ResourceLocation, Component> =
        LinkedHashMap<ResourceLocation, Component>().apply {
            components.forEach { component ->
                val id = ComponentDescriptorRegistry.idFor(component::class)
                    ?: error("Component descriptor not found for ${component::class.qualifiedName}")
                put(id, component)
            }
        }
}

@Serializable
data class EntitySnapshot(
    @Serializable(with = ComponentListSerializer::class)
    override val components: List<@Polymorphic Component> = emptyList(),
) : Snapshot() {
    @Transient
    var entity: Entity? = null

    fun withEntity(entity: Entity): EntitySnapshot = copy().also { it.entity = entity }

    fun dropLooseOnDeathComponents(): EntitySnapshot = copy(
        components = components.filterNot { component ->
            ComponentDescriptorRegistry.descriptorOrNull(component::class)?.persistencePolicy == ComponentPersistencePolicy.LOOSE_ON_DEATH
        },
    )
}

object ComponentListSerializer : KSerializer<List<Component>> {
    private val delegate = ListSerializer(PolymorphicSerializer(Component::class))

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: List<Component>) {
        delegate.serialize(encoder, value)
    }

    override fun deserialize(decoder: Decoder): List<Component> {
        val result = mutableListOf<Component>()
        val composite = decoder.beginStructure(descriptor)

        val size = composite.decodeCollectionSize(descriptor)
        for (i in 0 until size) {
            try {
                result.add(composite.decodeSerializableElement(descriptor, i, PolymorphicSerializer(Component::class)))
            } catch (e: Exception) {
                HollowEngine.LOGGER.info("Missing component: ${e.message}")
            }
        }
        composite.endStructure(descriptor)
        return result
    }
}

@Serializable
data class LevelSnapshot(
    val id: @Serializable(ForUuid::class) UUID = UUID.randomUUID(),
    val dimension: @Serializable(ForResourceLocation::class) ResourceLocation? = null,
    override val components: List<@Polymorphic Component> = emptyList(),
) : Snapshot()
