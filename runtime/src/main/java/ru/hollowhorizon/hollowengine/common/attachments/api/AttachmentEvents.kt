@file:JvmName("AttachmentHelper")

package ru.hollowhorizon.hollowengine.common.attachments.api

import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.entity.EntityEvent
import ru.hollowhorizon.hollowengine.common.events.entity.EntityLoadedEvent
import ru.hollowhorizon.hollowengine.common.events.entity.player.PlayerEvent

/**
 * Runs before every other listener of this event on purpose: scripts subscribe to it to attach nodes,
 * and they can only do that once the attachments point at the instance that just joined.
 */
@SubscribeEvent(priority = Int.MAX_VALUE)
fun onEntityLoaded(event: EntityLoadedEvent) {
    AttachmentRegistry.onEntityJoinedLevel(event.entity)
}

@SubscribeEvent
fun onEntityDimensionChanged(event: EntityEvent.ChangeDimension) {
    if (event.entity is Player) return
    AttachmentRegistry.onDimensionChanged(event.entity, event.new, event.from, event.to)
}

@SubscribeEvent
fun onPlayerDimensionChanged(event: PlayerEvent.ChangeDimension) {
    AttachmentRegistry.onPlayerDimensionChanged(event.player, event.from, event.to)
}
