package ru.hollowhorizon.hollowengine.common.geary.snapshot

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import ru.hollowhorizon.hollowengine.common.geary.api.Component
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentDescriptorRegistry
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentPersistencePolicy
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForResourceLocation
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForUuid
import java.util.UUID

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

@Serializable
data class LevelSnapshot(
    val id: @Serializable(ForUuid::class) UUID = UUID.randomUUID(),
    val dimension: @Serializable(ForResourceLocation::class) ResourceLocation? = null,
    override val components: List<@Polymorphic Component> = emptyList(),
) : Snapshot()
