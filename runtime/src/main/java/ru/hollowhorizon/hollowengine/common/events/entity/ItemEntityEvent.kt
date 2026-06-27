package ru.hollowhorizon.hollowengine.common.events.entity

import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.common.events.Cancellable
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.events.factory.EventHandler

open class ItemEntityEvent(val entity: ItemEntity) : Event {
    class Toss(entity: ItemEntity, val player: Player): ItemEntityEvent(entity), Cancellable {
        companion object: EventHandler<Toss>()
        override var isCanceled: Boolean = false
    }
}
