package ru.hollowhorizon.hollowengine.common.geary.sync

import com.mineinabyss.geary.addons.dsl.createAddon
import com.mineinabyss.geary.datatypes.Component
import com.mineinabyss.geary.datatypes.ComponentId
import com.mineinabyss.geary.datatypes.isRelation
import com.mineinabyss.geary.helpers.componentId
import com.mineinabyss.geary.modules.Geary
import com.mineinabyss.geary.observers.events.OnRemove
import com.mineinabyss.geary.observers.events.OnSet
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.common.geary.ListString
import ru.hollowhorizon.hollowengine.common.geary.components.Model
import ru.hollowhorizon.hollowengine.common.geary.tracking.MCEntity
import ru.hollowhorizon.hollowengine.common.network.sendTrackingEntityAndSelf

data class SyncableComponentsBuilder(
    val world: Geary,
    var overrideSyncs: ComponentId? = null,
    val syncableComponents: MutableSet<ComponentId> = mutableSetOf(),
) {
    inline fun <reified T : Component> syncable() {
        syncableComponents.add(world.componentId<T>())
    }

    fun build(): SyncableComponentsModule {
        return SyncableComponentsModule(
            syncs = overrideSyncs ?: world.componentId<Syncs>(),
            syncableComponents = syncableComponents.toSet()
        )
    }
}

data class SyncableComponentsModule(
    val syncs: ComponentId,
    val syncableComponents: Set<ComponentId>,
)

val SyncableComponents = createAddon<SyncableComponentsBuilder, SyncableComponentsModule>(
    "Syncable Components",
    { SyncableComponentsBuilder(geary) }
) {
    val module = configuration.build()

    with(geary) {
        registerSyncing<ListString>()
        registerSyncing<Model>()

        observeSource<OnSet>().exec {
            val component = source.toGeary()
            if (!entity.hasRelation<Syncs>(component) || source.isRelation()) return@exec
            val mcEntity = entity.get<MCEntity>() ?: return@exec
            val target = entity.get(source) ?: return@exec
            ComponentUpdatePacket(mcEntity.id, target).sendTrackingEntityAndSelf(mcEntity)
        }

        observeSource<OnRemove>().exec {
            val component = source.toGeary()
            if (!entity.hasRelation<Syncs>(component) || source.isRelation()) return@exec
            val mcEntity = entity.get<MCEntity>() ?: return@exec
            val key = component.get<ResourceLocation>() ?: return@exec
            ComponentRemovePacket(mcEntity.id, key).sendTrackingEntityAndSelf(mcEntity)
        }
    }

    module
}