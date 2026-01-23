package ru.hollowhorizon.hollowengine.common.geary.sync

import com.mineinabyss.geary.addons.dsl.createAddon
import com.mineinabyss.geary.datatypes.Component
import com.mineinabyss.geary.datatypes.ComponentId
import com.mineinabyss.geary.helpers.componentId
import com.mineinabyss.geary.modules.Geary
import com.mineinabyss.geary.modules.observe
import com.mineinabyss.geary.modules.observeWithData
import com.mineinabyss.geary.observers.events.OnRemove

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

    systems {
        observeWithData<SyncEvent>().exec {
            logger.i("Observing ${event.tag}")
        }
        observe<OnRemove>().exec {
            logger.i("Removed $this")
        }
    }

    module
}