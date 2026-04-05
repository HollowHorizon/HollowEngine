package ru.hollowhorizon.hollowengine.common.geary.sync

import com.mineinabyss.geary.addons.dsl.createAddon
import com.mineinabyss.geary.datatypes.Component
import com.mineinabyss.geary.datatypes.ComponentId
import com.mineinabyss.geary.datatypes.Entity
import com.mineinabyss.geary.datatypes.isRelation
import com.mineinabyss.geary.helpers.componentId
import com.mineinabyss.geary.modules.Geary
import com.mineinabyss.geary.observers.events.OnRemove
import com.mineinabyss.geary.observers.events.OnSet
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.entity.EntityTrackingEvent
import ru.hollowhorizon.hollowengine.common.events.entity.player.PlayerEvent
import ru.hollowhorizon.hollowengine.common.geary.api.entity
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentDescriptorRegistry
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentSyncPolicy
import ru.hollowhorizon.hollowengine.common.geary.snapshot.applySnapshot
import ru.hollowhorizon.hollowengine.common.geary.snapshot.snapshotOf
import ru.hollowhorizon.hollowengine.common.geary.tracking.MCEntity
import ru.hollowhorizon.hollowengine.common.network.sendTrackingEntityAndSelf

data class SyncableComponentsBuilder(
    val world: Geary,
    var overrideSyncs: ComponentId? = null,
) {
    fun build(): SyncableComponentsModule {
        return SyncableComponentsModule(
            syncs = overrideSyncs ?: world.componentId<Syncs>(),
        )
    }
}

data class SyncableComponentsModule(
    val syncs: ComponentId,
)

val SyncableComponents = createAddon<SyncableComponentsBuilder, SyncableComponentsModule>(
    "Syncable Components",
    { SyncableComponentsBuilder(geary) }
) {
    val module = configuration.build()

    with(geary) {
        ComponentDescriptorRegistry.asSequence().map { it.value }
            .filter { it.syncPolicy == ComponentSyncPolicy.SYNC }
            .forEach { registerSyncingNoinline(it.value) }

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

fun Entity.getAllSyncable(): Set<Component> =
    getRelationsWithData<Syncs, Any>().mapTo(mutableSetOf()) { it.targetData }

@SubscribeEvent
fun startTracking(event: EntityTrackingEvent.Start) {
    val entity = event.entity
    entity.entity.getAllSyncable().forEach {
        ComponentUpdatePacket(entity.id, it)
            .send(event.player as ServerPlayer)
    }
}

@SubscribeEvent
fun onClone(event: PlayerEvent.Clone) {
    val old = event.oldPlayer
    val new = event.player

    val snapshot = snapshotOf(old.entity)
    val filtered = if (event.wasDeath) snapshot.dropLooseOnDeathComponents() else snapshot

    new.server?.coroutineScope?.launch {
        yield()
        applySnapshot(new.entity, filtered)
    }
}
