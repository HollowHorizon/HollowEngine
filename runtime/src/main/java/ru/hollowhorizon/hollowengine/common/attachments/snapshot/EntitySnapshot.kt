package ru.hollowhorizon.hollowengine.common.attachments.snapshot

import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.encodeCollection
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.attachments.api.Component
import ru.hollowhorizon.hollowengine.common.attachments.components.ComponentDescriptorRegistry
import ru.hollowhorizon.hollowengine.common.attachments.components.ComponentPersistencePolicy

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
    private val componentSerializer = PolymorphicSerializer(Component::class)

    override val descriptor: SerialDescriptor =
        ListSerializer(componentSerializer).descriptor

    override fun serialize(encoder: Encoder, value: List<Component>) {
        encoder.encodeCollection(descriptor, value.size) {
            value.forEachIndexed { index, component ->
                encodeSerializableElement(
                    descriptor,
                    index,
                    componentSerializer,
                    component
                )
            }
        }
    }

    override fun deserialize(decoder: Decoder): List<Component> {
        val result = mutableListOf<Component>()
        val composite = decoder.beginStructure(descriptor)

        fun decodeComponent(index: Int) {
            try {
                val component = composite.decodeSerializableElement(
                    descriptor,
                    index,
                    PolymorphicSerializer(Component::class)
                )
                result += component
            } catch (e: Exception) {
                HollowEngine.LOGGER.warn(
                    "Failed to deserialize component at index $index: ${e.message}",
                    e
                )
            }
        }

        val size = composite.decodeCollectionSize(descriptor)

        if (size >= 0) {
            for (index in 0 until size) decodeComponent(index)
        } else {
            while (true) {
                val index = composite.decodeElementIndex(descriptor)
                if (index == CompositeDecoder.DECODE_DONE) break
                decodeComponent(index)
            }
        }

        composite.endStructure(descriptor)
        return result
    }
}
