package ru.hollowhorizon.hc.common.events.entity

import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.LivingEntity
import ru.hollowhorizon.hc.common.events.Cancelable
import ru.hollowhorizon.hc.common.events.Event

class LivingEntityDeathEvent(val entity: LivingEntity, val source: DamageSource) : Event, Cancelable {
    override var isCanceled: Boolean = false
}
