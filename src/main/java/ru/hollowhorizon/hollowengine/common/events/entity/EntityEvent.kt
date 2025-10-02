package ru.hollowhorizon.hollowengine.common.events.entity

import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import ru.hollowhorizon.hollowengine.common.components.ComponentDispatcher
import ru.hollowhorizon.hollowengine.common.events.Cancelable
import ru.hollowhorizon.hollowengine.common.events.ComponentDispatcherEvent
import ru.hollowhorizon.hollowengine.common.events.Event

open class EntityEvent(val entity: Entity) : ComponentDispatcherEvent<Entity> {
    override val owner = entity as ComponentDispatcher

    class Hurt(entity: Entity, val source: DamageSource, var amount: Float): EntityEvent(entity), Cancelable {
        override var isCanceled: Boolean = false
    }

    class ChangeDimension(entity: Entity, val new: Entity, val from: Level, val to: Level) : EntityEvent(entity)
}