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
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import ru.hollowhorizon.hollowengine.api.Synced
import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.entity.EntityTrackingEvent
import ru.hollowhorizon.hollowengine.common.events.entity.player.PlayerEvent
import ru.hollowhorizon.hollowengine.common.geary.api.entity
import ru.hollowhorizon.hollowengine.common.geary.api.entityId
import ru.hollowhorizon.hollowengine.common.geary.api.geary
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentRegistry
import ru.hollowhorizon.hollowengine.common.geary.components.Model
import ru.hollowhorizon.hollowengine.common.geary.tracking.MCEntity
import ru.hollowhorizon.hollowengine.common.geary.tracking.datastore.decodeComponents
import ru.hollowhorizon.hollowengine.common.geary.tracking.datastore.encodeComponentsTo
import ru.hollowhorizon.hollowengine.common.geary.tracking.datastore.loadComponentsFrom
import ru.hollowhorizon.hollowengine.common.network.sendTrackingEntityAndSelf
import kotlin.reflect.full.hasAnnotation

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
        ComponentRegistry.filter { it.value.hasAnnotation<Synced>() }.forEach {
            registerSyncingNoinline(it.value)
        }
        registerSyncing<Model>(saveOnDeath = true)

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
    val isDeath = event.wasDeath

    val tag = CompoundTag()
    with(old.level().geary) { old.entityId.encodeComponentsTo(tag) }
    new.server?.coroutineScope?.launch {
        yield() // Ждём конца тика чтобы пакеты отправились игроку с нужным id
        with(new.level().geary) {
            new.entity.loadComponentsFrom(tag.decodeComponents())
        }
    }
}