package ru.hollowhorizon.hollowengine.common.codeblocks.runtime

import net.minecraft.world.entity.Entity
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.events.factory.EventHandler

class OwnerScopeRestoredEvent(
    val entity: Entity,
) : Event {
    companion object : EventHandler<OwnerScopeRestoredEvent>()
}
