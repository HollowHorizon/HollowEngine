package ru.hollowhorizon.hollowengine.common.codeblocks.runtime

import net.minecraft.world.entity.Entity
import ru.hollowhorizon.hollowengine.common.events.Event

class OwnerScopeRestoredEvent(
    val entity: Entity,
) : Event
