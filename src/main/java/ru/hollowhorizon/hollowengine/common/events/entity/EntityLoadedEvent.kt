package ru.hollowhorizon.hollowengine.common.events.entity

import net.minecraft.world.entity.Entity
import ru.hollowhorizon.hollowengine.common.events.Event
import java.util.UUID

class EntityLoadedEvent(val entity: Entity) : Event {
    val uuid: UUID get() = entity.uuid
}
