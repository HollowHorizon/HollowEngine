package ru.hollowhorizon.hollowengine.common.events.entity.player

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.player.Player.BedSleepingProblem
import ru.hollowhorizon.hollowengine.common.events.Event

open class PlayerEvent(val player: Player) : Event {
    class Clone(player: Player, val oldPlayer: Player, val wasDeath: Boolean) : PlayerEvent(player)

    class Join(player: Player) : PlayerEvent(player)
    class Leave(player: Player) : PlayerEvent(player)
    class ChangeDimension(player: Player, val from: ServerLevel, val to: ServerLevel) : PlayerEvent(player)
    class Respawn(player: Player, val isReturnFromEnd: Boolean) : PlayerEvent(player)
    class SleepInBed(player: Player, var problem: BedSleepingProblem? = null, val pos: BlockPos) : PlayerEvent(player)
    class Wakeup(player: Player, val wakeImmediately: Boolean, val updateLevelForSleepingPlayers: Boolean) : PlayerEvent(player)
}
