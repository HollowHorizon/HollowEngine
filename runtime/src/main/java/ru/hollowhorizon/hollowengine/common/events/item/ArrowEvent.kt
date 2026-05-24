package ru.hollowhorizon.hollowengine.common.events.item

import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import ru.hollowhorizon.hollowengine.common.events.Cancellable
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.events.factory.EventHandler
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptBinding

@ScriptBinding
open class ArrowEvent : Event {
    @ScriptBinding
    class Nock(
        var stack: ItemStack,
        val level: Level,
        val player: Player,
        val usedHand: InteractionHand,
        val hasAmmo: Boolean,
    ) : ArrowEvent() {
        companion object: EventHandler<Nock>()
    }

    @ScriptBinding
    class Loose(
        val stack: ItemStack,
        val level: Level,
        val player: Player,
        var charge: Int,
        val hasAmmo: Boolean,
    ) : ArrowEvent(), Cancellable {
        companion object: EventHandler<Loose>()
        override var isCanceled = false
    }
}
