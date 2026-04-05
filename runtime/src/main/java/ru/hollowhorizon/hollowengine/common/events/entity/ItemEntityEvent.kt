package ru.hollowhorizon.hollowengine.common.events.entity

import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.common.events.Cancelable
import ru.hollowhorizon.hollowengine.common.events.Event

open class ItemEntityEvent(val entity: ItemEntity) : Event {
    class Toss(entity: ItemEntity, val player: Player): ItemEntityEvent(entity), Cancelable {
        override var isCanceled: Boolean = false
    }
}