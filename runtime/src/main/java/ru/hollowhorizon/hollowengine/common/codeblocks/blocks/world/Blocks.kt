package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.world

import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.valueproviders.IntProvider
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksColors
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.DefaultText
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.NumberBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.types.BlockPosBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.types.PositionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.currentFile
import ru.hollowhorizon.hollowengine.common.codeblocks.typeOf

@Serializable
@SerialName("hollowengine:world/update_block")
class UpdateBlockBlock : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.WORLDS

    val world by input<ResourceKey<Level>>()
    val pos by inputDefault<BlockPos>("pos") { BlockPosBlock() }

    override suspend fun execute() {
        val level = currentFile().system.owner.getLevel(world()) ?: return
        val pos = pos()
        level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3)
    }

    override fun InputSlotScope.composeContent() {
        DefaultText("hollowengine.gui.codeblocks.label.world_update_block_in".lang)
        InputSlot(pos)
        DefaultText("hollowengine.gui.codeblocks.label.world_in_world".lang)
        InputSlot(world)
    }
}

@Serializable
@SerialName("hollowengine:world/set_block")
class SetBlockBlock: StatementBlock() {
    override val color: Color get() = CodeBlocksColors.WORLDS

    val world by input<ResourceKey<Level>>()
    val pos by inputDefault<BlockPos>("pos") { BlockPosBlock() }
    val blockState by input<BlockState>("blockState")

    override suspend fun execute() {
        val level = currentFile().system.owner.getLevel(world()) ?: return
        val pos = pos()
        val blockState = blockState()
        level.setBlockAndUpdate(pos, blockState)
    }

    override fun InputSlotScope.composeContent() {
        DefaultText("hollowengine.gui.codeblocks.label.world_set_block".lang)
        InputSlot(blockState)
        DefaultText("hollowengine.gui.codeblocks.label.world_to".lang)
        InputSlot(pos)
        DefaultText("hollowengine.gui.codeblocks.label.world_in_world".lang)
        InputSlot(world)
    }
}

@Serializable
@SerialName("hollowengine:world/get_block")
class GetBlockBlock: ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.WORLDS

    val world by input<ResourceKey<Level>>()
    val pos by inputDefault<BlockPos>("pos") { BlockPosBlock() }

    @Transient
    override val expressionType: ExpressionType = typeOf<BlockState>()
    override suspend fun execute(): BlockState {
        val level = currentFile().system.owner.getLevel(world()) ?: throw IllegalStateException("Level not found: ${world()}")
        val pos = pos()
        return level.getBlockState(pos)
    }

    override fun InputSlotScope.composeContent() {
        DefaultText("hollowengine.gui.codeblocks.label.world_get_block_in".lang)
        InputSlot(pos)
        DefaultText("hollowengine.gui.codeblocks.label.world_in_world".lang)
        InputSlot(world)
    }
}

@Serializable
@SerialName("hollowengine:world/remove_block")
class RemoveBlockBlock : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.WORLDS

    val world by input<ResourceKey<Level>>()
    val pos by inputDefault<BlockPos>("pos") { BlockPosBlock() }

    override suspend fun execute() {
        val level = currentFile().system.owner.getLevel(world()) ?: return
        val pos = pos()
        level.removeBlock(pos, false)
    }

    override fun InputSlotScope.composeContent() {
        DefaultText("hollowengine.gui.codeblocks.label.world_remove_block_in".lang)
        InputSlot(pos)
        DefaultText("hollowengine.gui.codeblocks.label.world_in_world".lang)
        InputSlot(world)
    }
}

@Serializable
@SerialName("hollowengine:world/rotate_block")
class RotateBlockBlock: StatementBlock() {
    override val color: Color get() = CodeBlocksColors.WORLDS

    val world by input<ResourceKey<Level>>()
    val pos by inputDefault<BlockPos>("pos") { BlockPosBlock() }
    val rotation by input<Rotation>("rotation")

    override suspend fun execute() {
        val level = currentFile().system.owner.getLevel(world()) ?: return
        val pos = pos()
        val blockState = level.getBlockState(pos)

        val rotatedState = blockState.rotate(rotation())
        level.setBlockAndUpdate(pos, rotatedState)
    }

    override fun InputSlotScope.composeContent() {
        DefaultText("hollowengine.gui.codeblocks.label.world_rotate_block_in".lang)
        InputSlot(pos)
        DefaultText("hollowengine.gui.codeblocks.label.world_in_direction".lang)
        InputSlot(rotation)
        DefaultText("hollowengine.gui.codeblocks.label.world_in_world".lang)
        InputSlot(world)
    }
}

@Serializable
@SerialName("hollowengine:world/spawn_entity")
class SpawnEntityBlock: ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.WORLDS

    val world by input<ResourceKey<Level>>()
    val entityType by input<EntityType<*>>("entityType")
    val position by inputDefault<Vec3>("pos") { PositionBlock() }

    override suspend fun execute() {
        val level = currentFile().system.owner.getLevel(world()) ?: return
        val entityType = entityType()
        val entity = entityType.create(level) ?: return

        entity.setPos(position())
        level.addFreshEntity(entity)
    }

    override fun InputSlotScope.composeContent() {
        DefaultText("hollowengine.gui.codeblocks.label.world_spawn_entity".lang)
        InputSlot(entityType)
        DefaultText("hollowengine.gui.codeblocks.label.world_at_coords".lang)
        InputSlot(position)
        DefaultText("hollowengine.gui.codeblocks.label.world_in_world".lang)
        InputSlot(world)
    }

    @Transient
    override val expressionType: ExpressionType = typeOf<Entity>()
}

@Serializable
@SerialName("hollowengine:world/has_sky_at")
class HasSkyAtBlock: ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.WORLDS

    val world by input<ResourceKey<Level>>()
    val pos by inputDefault<BlockPos>("pos") { BlockPosBlock() }

    @Transient
    override val expressionType: ExpressionType = typeOf<Boolean>()
    override suspend fun execute(): Boolean {
        val level = currentFile().system.owner.getLevel(world()) ?: throw IllegalStateException("Level not found: ${world()}")
        val pos = pos()
        return level.canSeeSky(pos)
    }

    override fun InputSlotScope.composeContent() {
        DefaultText("hollowengine.gui.codeblocks.label.world_at_coords".lang)
        InputSlot(pos)
        DefaultText("hollowengine.gui.codeblocks.label.world_can_see_sky".lang)
        InputSlot(world)
    }
}

@Serializable
@SerialName("hollowengine:world/get_time")
class GetTimeBlock: ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.WORLDS

    val world by input<ResourceKey<Level>>()

    @Transient
    override val expressionType: ExpressionType = typeOf<Long>()
    override suspend fun execute(): Long {
        val level = currentFile().system.owner.getLevel(world()) ?: throw IllegalStateException("Level not found: ${world()}")
        return level.dayTime
    }

    override fun InputSlotScope.composeContent() {
        DefaultText("hollowengine.gui.codeblocks.label.world_time_in".lang)
        InputSlot(world)
    }
}

@Serializable
@SerialName("hollowengine:world/set_time")
class SetTimeBlock: StatementBlock() {
    override val color: Color get() = CodeBlocksColors.WORLDS

    val world by input<ResourceKey<Level>>()
    val time by inputDefault<Number>("time") { NumberBlock(0.0) }

    override suspend fun execute() {
        val level = currentFile().system.owner.getLevel(world()) ?: return
        level.dayTime = time().toLong()
    }

    override fun InputSlotScope.composeContent() {
        DefaultText("hollowengine.gui.codeblocks.label.world_set_time_in".lang)
        InputSlot(world)
        DefaultText("hollowengine.gui.codeblocks.label.world_to".lang)
        InputSlot(time)
    }
}

enum class Weather { CLEAR, RAIN, THUNDER }

@Serializable
@SerialName("hollowengine:world/get_weather")
class GetWeatherBlock: ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.WORLDS

    val world by input<ResourceKey<Level>>()
    @Transient
    override val expressionType: ExpressionType = typeOf<Weather>()
    override suspend fun execute(): Weather {
        val level = currentFile().system.owner.getLevel(world()) ?: throw IllegalStateException("Level not found: ${world()}")
        return when {
            level.isRaining -> Weather.RAIN
            level.isThundering -> Weather.THUNDER
            else -> Weather.CLEAR
        }
    }
    override fun InputSlotScope.composeContent() {
        DefaultText("hollowengine.gui.codeblocks.label.world_weather_in".lang)
        InputSlot(world)
    }
}

@Serializable
@SerialName("hollowengine:world/set_weather")
class SetWeatherBlock: StatementBlock() {
    override val color: Color get() = CodeBlocksColors.WORLDS

    val world by input<ResourceKey<Level>>("world")
    val weather by input<Weather>("weather")

    override suspend fun execute() {
        val level = currentFile().system.owner.getLevel(world()) ?: throw IllegalStateException("Level not found: ${world()}")

        when(weather()) {
            Weather.CLEAR -> level.setWeatherParameters(getDuration(level, -1, ServerLevel.RAIN_DELAY), 0, false, false)
            Weather.RAIN -> level.setWeatherParameters(0, getDuration(level, -1, ServerLevel.RAIN_DURATION), true, false)
            Weather.THUNDER -> level.setWeatherParameters(0, getDuration(level, -1, ServerLevel.THUNDER_DURATION), true, true)
        }
    }

    private fun getDuration(level: Level, time: Int, timeProvider: IntProvider): Int {
        return if (time == -1) timeProvider.sample(level.getRandom()) else time
    }

    override fun InputSlotScope.composeContent() {
        DefaultText("hollowengine.gui.codeblocks.label.world_set_weather".lang)
        InputSlot(weather)
        DefaultText("hollowengine.gui.codeblocks.label.world_in_world".lang)
        InputSlot(world)
    }
}