package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.world

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
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockContext
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.DefaultText
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.typeOf

@Serializable
@SerialName("hollowengine:world/update_block")
class UpdateBlockBlock : StatementBlock() {
    val world by input<ResourceKey<Level>>()
    val pos by input<BlockPos>("pos")

    override suspend fun BlockContext.execute() {
        val level = server.getLevel(world()) ?: return
        val pos = pos()
        level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3)
    }

    override fun InputSlotScope.composeContent() {
        DefaultText("Обновить блок в")
        InputSlot(pos)
        DefaultText("в мире")
        InputSlot(world)
    }
}

@Serializable
@SerialName("hollowengine:world/set_block")
class SetBlockBlock: StatementBlock() {
    val world by input<ResourceKey<Level>>()
    val pos by input<BlockPos>("pos")
    val blockState by input<BlockState>("blockState")

    override suspend fun BlockContext.execute() {
        val level = server.getLevel(world()) ?: return
        val pos = pos()
        val blockState = blockState()
        level.setBlockAndUpdate(pos, blockState)
    }

    override fun InputSlotScope.composeContent() {
        DefaultText("Установить блок")
        InputSlot(blockState)
        DefaultText("в")
        InputSlot(pos)
        DefaultText("в мире")
        InputSlot(world)
    }
}

@Serializable
@SerialName("hollowengine:world/get_block")
class GetBlockBlock: ExpressionBlock() {
    val world by input<ResourceKey<Level>>()
    val pos by input<BlockPos>("pos")
    @Transient
    override val expressionType: ExpressionType = typeOf<BlockState>()
    override suspend fun BlockContext.execute(): BlockState {
        val level = server.getLevel(world()) ?: throw IllegalStateException("Level not found: ${world()}")
        val pos = pos()
        return level.getBlockState(pos)
    }
    override fun InputSlotScope.composeContent() {
        DefaultText("Получить блок в")
        InputSlot(pos)
        DefaultText("в мире")
        InputSlot(world)
    }
}

@Serializable
@SerialName("hollowengine:world/remove_block")
class RemoveBlockBlock : StatementBlock() {
    val world by input<ResourceKey<Level>>()
    val pos by input<BlockPos>("pos")

    override suspend fun BlockContext.execute() {
        val level = server.getLevel(world()) ?: return
        val pos = pos()
        level.removeBlock(pos, false)
    }

    override fun InputSlotScope.composeContent() {
        DefaultText("Удалить блок в")
        InputSlot(pos)
        DefaultText("в мире")
        InputSlot(world)
    }
}

@Serializable
@SerialName("hollowengine:world/rotate_block")
class RotateBlockBlock: StatementBlock() {
    val world by input<ResourceKey<Level>>()
    val pos by input<BlockPos>("pos")
    val rotation by input<Rotation>("rotation")

    override suspend fun BlockContext.execute() {
        val level = server.getLevel(world()) ?: return
        val pos = pos()
        val blockState = level.getBlockState(pos)

        val rotatedState = blockState.rotate(rotation())
        level.setBlockAndUpdate(pos, rotatedState)
    }

    override fun InputSlotScope.composeContent() {
        DefaultText("Повернуть блок в")
        InputSlot(pos)
        DefaultText("в направлении")
        InputSlot(rotation)
        DefaultText("в мире")
        InputSlot(world)
    }
}

@Serializable
@SerialName("hollowengine:world/spawn_entity")
class SpawnEntityBlock: ExpressionBlock() {
    val world by input<ResourceKey<Level>>()
    val entityType by input<EntityType<*>>("entityType")
    val position by input<Vec3>("pos")

    override suspend fun BlockContext.execute() {
        val level = server.getLevel(world()) ?: return
        val entityType = entityType()
        val entity = entityType.create(level) ?: return

        entity.setPos(position())
        level.addFreshEntity(entity)
    }

    override fun InputSlotScope.composeContent() {
        DefaultText("Призвать сущность")
        InputSlot(entityType)
        DefaultText("в координатах")
        InputSlot(position)
        DefaultText("в мире")
        InputSlot(world)
    }

    @Transient
    override val expressionType: ExpressionType = typeOf<Entity>()
}

@Serializable
@SerialName("hollowengine:world/has_sky_at")
class HasSkyAtBlock: ExpressionBlock() {
    val world by input<ResourceKey<Level>>()
    val pos by input<BlockPos>("pos")
    @Transient
    override val expressionType: ExpressionType = typeOf<Boolean>()
    override suspend fun BlockContext.execute(): Boolean {
        val level = server.getLevel(world()) ?: throw IllegalStateException("Level not found: ${world()}")
        val pos = pos()
        return level.canSeeSky(pos)
    }
    override fun InputSlotScope.composeContent() {
        DefaultText("По координатам")
        InputSlot(pos)
        DefaultText("видно небо в мире")
        InputSlot(world)
    }
}

@Serializable
@SerialName("hollowengine:world/get_time")
class GetTimeBlock: ExpressionBlock() {
    val world by input<ResourceKey<Level>>()
    @Transient
    override val expressionType: ExpressionType = typeOf<Long>()
    override suspend fun BlockContext.execute(): Long {
        val level = server.getLevel(world()) ?: throw IllegalStateException("Level not found: ${world()}")
        return level.dayTime
    }
    override fun InputSlotScope.composeContent() {
        DefaultText("Время в мире")
        InputSlot(world)
    }
}

@Serializable
@SerialName("hollowengine:world/set_time")
class SetTimeBlock: StatementBlock() {
    val world by input<ResourceKey<Level>>()
    val time by input<Number>("time")

    override suspend fun BlockContext.execute() {
        val level = server.getLevel(world()) ?: return
        level.dayTime = time().toLong()
    }

    override fun InputSlotScope.composeContent() {
        DefaultText("Установить время в мире")
        InputSlot(world)
        DefaultText("в")
        InputSlot(time)
    }
}

enum class Weather { CLEAR, RAIN, THUNDER }

@Serializable
@SerialName("hollowengine:world/get_weather")
class GetWeatherBlock: ExpressionBlock() {
    val world by input<ResourceKey<Level>>()
    @Transient
    override val expressionType: ExpressionType = typeOf<Weather>()
    override suspend fun BlockContext.execute(): Weather {
        val level = server.getLevel(world()) ?: throw IllegalStateException("Level not found: ${world()}")
        return when {
            level.isRaining -> Weather.RAIN
            level.isThundering -> Weather.THUNDER
            else -> Weather.CLEAR
        }
    }
    override fun InputSlotScope.composeContent() {
        DefaultText("Погода в мире")
        InputSlot(world)
    }
}

@Serializable
@SerialName("hollowengine:world/set_weather")
class SetWeatherBlock: StatementBlock() {
    val world by input<ResourceKey<Level>>("world")
    val weather by input<Weather>("weather")

    override suspend fun BlockContext.execute() {
        val level = server.getLevel(world()) ?: throw IllegalStateException("Level not found: ${world()}")

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
        DefaultText("Установить погоду")
        InputSlot(weather)
        DefaultText("в мире")
        InputSlot(world)
    }
}