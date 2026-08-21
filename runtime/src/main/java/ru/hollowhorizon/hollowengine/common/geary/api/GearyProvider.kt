@file:JvmName("GearyHelper")

package ru.hollowhorizon.hollowengine.common.geary.api

import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.entity.EntityEvent
import ru.hollowhorizon.hollowengine.common.events.entity.EntityLoadedEvent
import ru.hollowhorizon.hollowengine.common.events.entity.player.PlayerEvent
import ru.hollowhorizon.hollowengine.common.geary.tracking.MCEntity

/**
 * Points the attachments of an already-known entity at [entity] when the game hands out a new instance
 * for the same UUID. Does nothing for entities that have no attachments.
 */
fun rebindIfPresent(level: Level, entity: MCEntity) = GearyRuntimeState.rebindIfPresent(level, entity)

@SubscribeEvent
fun onEntityLoaded(event: EntityLoadedEvent) {
    rebindIfPresent(event.entity.level(), event.entity)
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
