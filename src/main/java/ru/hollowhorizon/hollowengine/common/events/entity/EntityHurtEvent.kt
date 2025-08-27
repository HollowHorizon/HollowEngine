package ru.hollowhorizon.hollowengine.common.events.entity

import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import ru.hollowhorizon.hollowengine.common.events.Cancelable
import ru.hollowhorizon.hollowengine.common.events.Event

open class EntityHurtEvent(val entity: Entity, val source: DamageSource, var amount: Float): Event, Cancelable {
    override var isCanceled: Boolean = false
}