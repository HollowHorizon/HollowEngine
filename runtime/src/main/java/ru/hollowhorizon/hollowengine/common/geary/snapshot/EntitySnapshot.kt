package ru.hollowhorizon.hollowengine.common.geary.snapshot

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.common.geary.anchor.AnchorComponent
import ru.hollowhorizon.hollowengine.common.geary.anchor.StableKeyComponent
import ru.hollowhorizon.hollowengine.common.geary.api.Component
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentDescriptorRegistry
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentPersistencePolicy
import java.util.*

@Serializable
data class EntitySnapshot(
    val version: Int = CURRENT_VERSION,
    val components: List<@Polymorphic Component> = emptyList(),
) {
    val anchor: AnchorComponent get() = components.filterIsInstance<AnchorComponent>().first()
    val stableKey: UUID get() = components.filterIsInstance<StableKeyComponent>().first().value

    fun componentById(): LinkedHashMap<ResourceLocation, Component> =
        LinkedHashMap<ResourceLocation, Component>().apply {
            components.forEach { component ->
                val id = ComponentDescriptorRegistry.idFor(component::class)
                    ?: error("Component descriptor not found for ${component::class.qualifiedName}")
                put(id, component)
            }
        }

    fun dropLooseOnDeathComponents(): EntitySnapshot = copy(
        components = components.filterNot { component ->
            ComponentDescriptorRegistry.descriptorOrNull(component::class)?.persistencePolicy == ComponentPersistencePolicy.LOOSE_ON_DEATH
        })

    companion object {
        const val CURRENT_VERSION: Int = 3
    }
}

inline fun <reified T : Component> EntitySnapshot.withComponent(component: T): EntitySnapshot =
    copy(components = components.filter { it !is T } + component)