package ru.hollowhorizon.hollowengine.common.events.entity

import net.minecraft.world.entity.Entity
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.events.factory.EventHandler
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptBinding
import java.util.*

@ScriptBinding
class EntityLoadedEvent(val entity: Entity) : Event {
    companion object : EventHandler<EntityLoadedEvent>()

    val uuid: UUID get() = entity.uuid
}
