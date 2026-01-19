package ru.hollowhorizon.hollowengine.common.fleks

import com.github.quillraven.fleks.Entity
import com.github.quillraven.fleks.IntervalSystem
import com.github.quillraven.fleks.Snapshot
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import kotlinx.serialization.Contextual
import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.common.fleks.components.EntityComponent
import ru.hollowhorizon.hollowengine.common.network.sendTrackingEntityAndSelf

class ComponentSyncSystem : IntervalSystem() {
    val trackedEntities = Object2ObjectOpenHashMap<Entity, ObjectOpenHashSet<MutableSyncedComponent<@Contextual Any>>>()

    fun startTracking(entity: Entity, component: MutableSyncedComponent<@Contextual Any>) {
        trackedEntities.getOrPut(entity) { ObjectOpenHashSet() }.add(component)
    }

    fun stopTracking(entity: Entity, component: MutableSyncedComponent<*>) {
        trackedEntities.getOrPut(entity) { ObjectOpenHashSet() }.remove(component)
    }

    override fun onTick() {
        with(world) {
            trackedEntities.forEach { (entity, components) ->
                if(entity.hasNo(EntityComponent)) {
                    HollowCore.LOGGER.warn("Entity ${entity.id} has no entity component")
                    return@forEach
                }
                val mcEntity = entity[EntityComponent].entity

                val snapshot = Snapshot(components.filter {
                    val sync = it.shouldSync()
                    if (sync) it.resetDirty()
                    sync
                }, listOf())
                if (snapshot.isNotEmpty()) ComponentUpdatePacket(mcEntity.id, snapshot).sendTrackingEntityAndSelf(mcEntity)
            }
        }
    }
}