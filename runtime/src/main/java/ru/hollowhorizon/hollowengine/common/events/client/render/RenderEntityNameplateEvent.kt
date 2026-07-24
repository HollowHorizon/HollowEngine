package ru.hollowhorizon.hollowengine.common.events.client.render

import net.minecraft.world.entity.Entity
import ru.hollowhorizon.hollowengine.common.events.ClientEvent
import ru.hollowhorizon.hollowengine.common.events.factory.EventHandler

class RenderEntityNameplateEvent(
    val entity: Entity,
    var isVisible: Boolean,
) : ClientEvent {
    companion object : EventHandler<RenderEntityNameplateEvent>()
}