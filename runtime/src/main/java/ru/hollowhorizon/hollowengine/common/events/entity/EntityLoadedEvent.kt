package ru.hollowhorizon.hollowengine.common.events.entity

import net.minecraft.world.entity.Entity
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.events.factory.EventHandler
import java.util.*

class EntityLoadedEvent(val entity: Entity) : Event {
    companion object : EventHandler<EntityLoadedEvent>()

    val uuid: UUID get() = entity.uuid
}
