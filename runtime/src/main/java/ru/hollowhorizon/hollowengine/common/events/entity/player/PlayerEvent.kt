package ru.hollowhorizon.hollowengine.common.events.entity.player

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.player.Player.BedSleepingProblem
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.events.factory.EventHandler
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptBinding

@ScriptBinding
open class PlayerEvent(val player: Player) : Event {
    @ScriptBinding
    class Clone(player: Player, val oldPlayer: Player, val wasDeath: Boolean) : PlayerEvent(player) {
        companion object : EventHandler<Clone>()
    }

    @ScriptBinding
    class Join(player: Player) : PlayerEvent(player) {
        companion object : EventHandler<Join>()
    }

    @ScriptBinding
    class Leave(player: Player) : PlayerEvent(player) {
        companion object : EventHandler<Leave>()
    }

    @ScriptBinding
    class ChangeDimension(player: Player, val from: ServerLevel, val to: ServerLevel) : PlayerEvent(player) {
        companion object : EventHandler<ChangeDimension>()
    }

    @ScriptBinding
    class Respawn(player: Player, val isReturnFromEnd: Boolean) : PlayerEvent(player) {
        companion object : EventHandler<Respawn>()
    }

    @ScriptBinding
    class SleepInBed(player: Player, var problem: BedSleepingProblem? = null, val pos: BlockPos) : PlayerEvent(player) {
        companion object : EventHandler<SleepInBed>()
    }

    @ScriptBinding
    class Wakeup(player: Player, val wakeImmediately: Boolean, val updateLevelForSleepingPlayers: Boolean) :
        PlayerEvent(player) {
        companion object : EventHandler<Wakeup>()
    }
}
