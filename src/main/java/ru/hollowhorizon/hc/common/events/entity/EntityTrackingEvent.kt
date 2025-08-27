package ru.hollowhorizon.hc.common.events.entity

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hc.common.events.Event

class EntityTrackingEvent(val player: Player, val entity: Entity) : Event