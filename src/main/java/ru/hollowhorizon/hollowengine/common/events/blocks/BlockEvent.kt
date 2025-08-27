package ru.hollowhorizon.hollowengine.common.events.blocks

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import ru.hollowhorizon.hollowengine.common.events.Cancelable
import ru.hollowhorizon.hollowengine.common.events.Event
import java.util.*

open class BlockEvent(var state: BlockState, val pos: BlockPos) : Event, Cancelable {
    override var isCanceled = false

    class NeighborNotify(
        state: BlockState,
        pos: BlockPos,
        val directions: EnumSet<Direction>,
    ): BlockEvent(state, pos)

    class Break(
        val level: Level,
        pos: BlockPos,
        state: BlockState,
        val player: Player
    ): BlockEvent(state, pos)

    class Placed(
        val player: Player,
        state: BlockState,
        pos: BlockPos
    ): BlockEvent(state, pos)
}