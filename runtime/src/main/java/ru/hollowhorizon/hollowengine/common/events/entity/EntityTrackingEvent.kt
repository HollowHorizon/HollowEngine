package ru.hollowhorizon.hollowengine.common.events.entity

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.common.events.Event

open class EntityTrackingEvent protected constructor(val player: Player, val entity: Entity) : Event {
    class Start(player: ServerPlayer, entity: Entity) : EntityTrackingEvent(player, entity)
    class Stop(player: ServerPlayer, entity: Entity) : EntityTrackingEvent(player, entity)
}