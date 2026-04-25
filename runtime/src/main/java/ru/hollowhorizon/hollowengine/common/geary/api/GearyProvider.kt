@file:JvmName("GearyHelper")

package ru.hollowhorizon.hollowengine.common.geary.api

import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.entity.EntityEvent
import ru.hollowhorizon.hollowengine.common.events.entity.EntityLoadedEvent
import ru.hollowhorizon.hollowengine.common.events.entity.player.PlayerEvent
import ru.hollowhorizon.hollowengine.common.geary.tracking.MCEntity

const val UNINITIALIZED_ENTITY_ID: Long = -1L

fun bind(level: Level, entity: MCEntity, entityId: Int = entity.id, previousEntityId: Int = entity.id): Long =
    GearyRuntimeState.bind(level, entity, entityId, previousEntityId)

fun bindIfInitialized(level: Level, entity: MCEntity): Long? =
    GearyRuntimeState.bindIfInitialized(level, entity)

fun move(old: Level, new: Level, mcEntity: MCEntity): Long =
    GearyRuntimeState.move(old, new, mcEntity)

@SubscribeEvent
fun onEntityLoaded(event: EntityLoadedEvent) {
    bindIfInitialized(event.entity.level(), event.entity)
}

@SubscribeEvent
fun onEntityDimensionChanged(event: EntityEvent.ChangeDimension) {
    if (event.entity is Player) return
    GearyRuntimeState.onDimensionChanged(event.entity, event.new, event.from, event.to)
}

@SubscribeEvent
fun onPlayerDimensionChanged(event: PlayerEvent.ChangeDimension) {
    GearyRuntimeState.onPlayerDimensionChanged(event.player, event.from, event.to)
}
