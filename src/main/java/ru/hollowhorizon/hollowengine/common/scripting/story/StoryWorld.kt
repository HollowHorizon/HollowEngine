package ru.hollowhorizon.hollowengine.common.scripting.story

import net.minecraft.core.BlockPos
import net.minecraft.nbt.Tag
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings
import net.minecraftforge.registries.ForgeRegistries
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.*
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.client.utils.toRL

class StoryWorld(val level: ServerLevel) {
    @Experimental
    fun placeBlock(block: String, pos: BlockPos, facing: FacingType? = null, nbt: Tag? = null, playSound: Boolean = true, playSoundFor: Player? = null) {
        val blockAsBlock = ForgeRegistries.BLOCKS.getValue(block.toRL()) ?: return

        this.level.setBlock(pos, blockAsBlock.defaultBlockState(), 1)
        if (playSound) this.level.playSound(
            playSoundFor,
            pos,
            blockAsBlock.getSoundType(blockAsBlock.defaultBlockState(),this.level, pos, null).placeSound,
            SoundSource.BLOCKS,
            1F,
            1F
        )
    }

    @Experimental
    fun destroyBlock(pos: BlockPos, playSound: Boolean) {
        this.level.destroyBlock(pos, false)
    }

    @NonExtendable @Internal
    fun useBlock(pos: BlockPos, item: String) {

    }

    @NonExtendable @Internal
    fun getOrSpawnStructure(name: String) {
        TODO("Make structure system")
    }

    @Experimental
    fun placeStructure(name: String, pos: BlockPos, facing: FacingType, animation: StructureAnimation) {
        //TODO: add structure animations

        val rotation = when (facing) {
            FacingType.NORTH -> Rotation.NONE
            FacingType.WEST -> Rotation.CLOCKWISE_90
            FacingType.SOUTH -> Rotation.CLOCKWISE_180
            FacingType.EAST -> Rotation.COUNTERCLOCKWISE_90
            else -> Rotation.NONE
        }

        this.level.structureManager.get(name.rl).orElseThrow()
            .placeInWorld(level, pos, pos, StructurePlaceSettings().setRotation(rotation), level.random, 3)
    }

    fun changeWeather(weather: WeatherType) {
        when (weather) {
            WeatherType.CLEAR -> this.level.setWeatherParameters(6000, 0, false, false)
            WeatherType.RAIN -> this.level.setWeatherParameters(0, 6000, true, false)
            WeatherType.THUNDER -> this.level.setWeatherParameters(0, 6000, true, true)
        }
    }

    fun setTime(time: Long) {
        this.level.dayTime = time
    }

    fun setTime(preset: TimePresets) {
        this.setTime(preset.time)
    }
}

//готовые пресеты, чтобы можно было прописать условно: world.setTime(TimePresets.DAY)
enum class TimePresets(val time: Long) {
    SUNSET(0L),
    DAY(1000L),
    NOON(6000L),
    NIGHT(13000L),
    MIDNIGHT(18000L)
}

@Internal
enum class StructureAnimation {
    NONE, DELAY, FROM_GROUND, FROM_CEILING
}

enum class FacingType {
    NORTH, SOUTH, WEST, EAST, UP, DOWN
}

enum class WeatherType {
    CLEAR, RAIN, THUNDER
}