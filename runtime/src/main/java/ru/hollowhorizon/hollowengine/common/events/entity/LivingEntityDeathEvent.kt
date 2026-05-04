package ru.hollowhorizon.hollowengine.common.events.entity

import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.LivingEntity
import ru.hollowhorizon.hollowengine.common.events.Cancellable
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.events.factory.EventHandler

class LivingEntityDeathEvent(val entity: LivingEntity, val source: DamageSource) : Event, Cancellable {
    companion object: EventHandler<LivingEntityDeathEvent>()
    override var isCanceled: Boolean = false
}
