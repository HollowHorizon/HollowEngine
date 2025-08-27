package ru.hollowhorizon.hollowengine.common.events.entity

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.common.events.Event

class EntityTrackingEvent(val player: Player, val entity: Entity) : Event