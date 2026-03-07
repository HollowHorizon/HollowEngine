package ru.hollowhorizon.hollowengine.common.geary.snapshot

import com.mineinabyss.geary.datatypes.Component
import com.mineinabyss.geary.prefabs.PrefabKey
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.api.Registerable
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentDescriptorRegistry
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentPersistencePolicy
import ru.hollowhorizon.hollowengine.common.geary.components.EditorHidden

@Registerable
@EditorHidden
@Serializable
@SerialName("hollowengine:prefab_refs")
data class PrefabRefsComponent(
    val refs: Set<PrefabKey> = emptySet(),
)

@Serializable
data class EntitySnapshot(
    val version: Int = CURRENT_VERSION,
    val prefabRefs: Set<PrefabKey> = emptySet(),
    val components: List<@Polymorphic Component> = emptyList(),
) {
    fun componentById(): LinkedHashMap<ResourceLocation, Component> = LinkedHashMap<ResourceLocation, Component>().apply {
        components.forEach { component ->
            val id = ComponentDescriptorRegistry.idFor(component::class)
                ?: error("Component descriptor not found for ${component::class.qualifiedName}")
            put(id, component)
        }
    }

    fun withResolvedPrefabs(resolvedComponents: List<Component>): EntitySnapshot {
        val merged = LinkedHashMap<ResourceLocation, Component>()
        resolvedComponents.forEach { component ->
            val id = ComponentDescriptorRegistry.idFor(component::class)
                ?: error("Component descriptor not found for ${component::class.qualifiedName}")
            merged[id] = component
        }
        components.forEach { component ->
            val id = ComponentDescriptorRegistry.idFor(component::class)
                ?: error("Component descriptor not found for ${component::class.qualifiedName}")
            merged[id] = component
        }
        return copy(components = merged.values.toList())
    }

    fun dropLooseOnDeathComponents(): EntitySnapshot = copy(
        components = components.filterNot { component ->
            ComponentDescriptorRegistry.descriptorOrNull(component::class)?.persistencePolicy == ComponentPersistencePolicy.LOOSE_ON_DEATH
        }
    )

    companion object {
        const val CURRENT_VERSION: Int = 2
    }
}

typealias PrefabDefinition = EntitySnapshot
