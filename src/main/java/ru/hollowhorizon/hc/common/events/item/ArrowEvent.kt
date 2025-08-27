package ru.hollowhorizon.hc.common.events.item

import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import ru.hollowhorizon.hc.common.events.Cancelable
import ru.hollowhorizon.hc.common.events.Event

open class ArrowEvent : Event {
    class Nock(
        var stack: ItemStack,
        val level: Level,
        val player: Player,
        val usedHand: InteractionHand,
        val hasAmmo: Boolean,
    ) : ArrowEvent()

    class Loose(
        val stack: ItemStack,
        val level: Level,
        val player: Player,
        var charge: Int,
        val hasAmmo: Boolean,
    ) : ArrowEvent(), Cancelable {
        override var isCanceled = false
    }
}